/*   
 * Copyright (c) 2010-2020 Founder Ltd. All Rights Reserved.   
 *   
 * This software is the confidential and proprietary information of   
 * Founder. You shall not disclose such Confidential Information   
 * and shall use it only in accordance with the terms of the agreements   
 * you entered into with Founder.   
 *   
 */
package cn.fi.main;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cn.fi.obj.Agent;
import cn.fi.obj.ApplicationInterface;
import cn.fi.obj.Redis;
import cn.fi.obj.SendMsg;
import cn.fi.obj.SocketInfo;
import cn.fi.obj.Station;
import cn.fi.obj.VapContainer;
//import cn.fi.obj.Vap;
import cn.fi.obj.msg.HandoffMessage;
import cn.fi.obj.msg.Message;
import cn.fi.obj.msg.ProbeInfoMessage;
import cn.fi.obj.msg.StaCtrlMessage;
import cn.fi.obj.msg.StationCtrlMessage;
import cn.fi.obj.sub.EventSubscription;
import cn.fi.obj.sub.NotificationCallback;
import cn.fi.obj.sub.NotificationCallbackContext;
import cn.fi.obj.sub.SubscriptionCallbackTuple;
import cn.fi.store.AgentManager;
import cn.fi.store.SendMsgBuffer;
import cn.fi.store.SocketManager;
import cn.fi.store.StationManager;
//import cn.fi.store.VapManager;

/**
 * 处理消息及管理视图的主类
 * 
 */

public class Master implements ApplicationInterface {
	static boolean showStoreView;
	static boolean showStatisticsView;

	// 长度常量
	public static final int MSG_TYPE_LEN = 3;// 消息格式中类型的长度
	public static final int MSG_LEN_LEN = 3;// 消息格式中长度字段的长度
	public static final int MSG_ID_LEN = 4;// 消息中ID长度
	public static final int MAX_MSG_LEN = 2048;// 最大命令占据字节数
	//(48=MSG_ID_LEN + MSG_TYPE_LEN + MSG_ID_LEN + 2 + ApMaclen + 2 + StaMaclen)
	public static final int MSG_DELSTA_NOSSID_LEN = 48;//4+3+3+2+12+2+12=48
	public static final int MSG_NEWSTA_NOSSID_LEN = 48;
	// 时间常量
	public static final long AGENT_HEARET_BEAT_AGING = 10;// agent和控制器心跳超时时间10s。
	public static final long VIRTUAL_STA_TIME_OUT = 60 * 5;// 虚拟MAC老化时间5min
	public static final long AGENT_TIME_OUT = 30;// Agent超时时间
	public static final long STA_TIME_OUT = 24;// Station的超时时间
	public static final long AGE_TIME_OUT = 60 * 10;// 老化时间 10min
	public static final long VAP_TIME_OUT = 60 * 10;// Vap超时时间
	public static final long INTVAL_TIME_OUT = 12;
	public static final long SHOW_TIME_OUT = 60 * 2;// 打印日志
	public static final long CMD_TIME_OUT = 3;// 接入控制应用
	public static final long AGETASK_PERIOD = 3;// 执行周期
	public static final long CMDTASK_PERIOD = 2;// 执行周期
	// 过滤信号强度
	public static final int MIN_RSSI = -75;
	// 状态常量
	public static final String ON_LINE = "online";// 表示在线状态（对于vap表示其已经分配给sta）
	public static final String OFF_LINE = "offline";// 表示离线状态
	// public static final String IDLE = "idle";//
	// 对于vap表示其已经可用（可分配给sta），当前处于空闲状态
	// public static final String WORKING = "working";// 工作状态
	public static final String ALL = "*";// 用于表示全部匹配
	public static final String delimiter = ";";// 消息分隔符
	public static final String MODE_5G = "011";
	public static final String MODE_2d4G = "010";
	public static final String MODE_DEFAULT = "000";
	// private ByteBuffer writeBuf = ByteBuffer.allocate(1024);// 消息字节缓冲
	private Log log = LogFactory.getLog(this.getClass());// 日志
	// 视图管理
	static AgentManager agentManager = new AgentManager();
	private static StationManager stationManager = new StationManager();
	private SendMsgBuffer sendMsgBuffer = new SendMsgBuffer();
	private SocketManager socketManager = new SocketManager();

	// private VapManager vapManager = new VapManager();
	private final HashMap<String, SubscriptionCallbackTuple> subscriptions = new HashMap<String, SubscriptionCallbackTuple>();// 用于存储应用订阅ID及订阅回调元组
	private final HashMap<String, Set<String>> msg2Subids = new HashMap<String, Set<String>>();// 存储消息类型以及对应订阅此消息的订阅ID
	private ConcurrentHashMap<String, AppCmd> cmdQueue = new ConcurrentHashMap<String, AppCmd>();
	// 用于执行线程的线程池
	private ExecutorService executor = Executors.newCachedThreadPool();
	private ScheduledExecutorService timerExecutor = Executors.newScheduledThreadPool(100);
	private ReadWriteLock scriptionsLock = new ReentrantReadWriteLock();// 读写锁，同步对订阅表的操作
	public static boolean REDIS_USED = false;
	public static Date date = new Date();
	public static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd/HH:mm:ss");

	/**
	 * 应用下发的命令类型，当前仅用于addVap命令
	 */
	private class AppCmd {
		StationCtrlMessage message;
		long firstHeart;

		public AppCmd(StationCtrlMessage message, long firstHeart) {
			super();
			this.message = message;
			this.firstHeart = firstHeart;
		}
	}

	/*
	 * 初始化定时器等
	 */
	public void init() {
		if (Master.REDIS_USED) {
			Redis.initRedis();
		}

		this.startTimerTask();
	}

	/**
	 * 检查是否需要发布消息， 此时的消息是与station相关的消息，包括要检测EventSubscription中的staMacAddr和status
	 */
	private void checkSubscribtion(Message message, String staMacAddress) {
		try {
			String type = message.getMsgType();
			scriptionsLock.readLock().lock();// 加读锁
			if (stationManager.getStation(staMacAddress) == null) {
				Master.consolePrintf("probeMsg stationMac be null!! why??");
				return;
			}
			if (msg2Subids.containsKey(type) && msg2Subids.get(type).size() != 0) {
				// Master.consolePrintf("++++");
				for (String subscriptionId : msg2Subids.get(type)) {
					if (subscriptions.get(subscriptionId).es.getStaMacAddress().equals(ALL)
							|| subscriptions.get(subscriptionId).es.getStaMacAddress().equals(staMacAddress)) {
						SubscriptionCallbackTuple tuble = subscriptions.get(subscriptionId);

						if (tuble.es.getStatus().equals(ALL) ||
								// stationManager.getStation(staMacAddress).getStatus().equals(tuble.es.getStatus())
								stationManager.getStation(staMacAddress).getStatus().equals(Master.ON_LINE)
								|| stationManager.getStation(staMacAddress).getStatus().equals(Master.OFF_LINE)) {
							// 通配或者匹配
							NotificationCallbackContext context = new NotificationCallbackContext(message);
							tuble.cb.exec(tuble.es, context);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.info("!!!app exec failed...");
		} finally {
			scriptionsLock.readLock().unlock();// 保障解锁
		}
	}

	/**
	 * 生成注册ID，如果此ID已经注册过，则说明重复进行了注册，则返回空
	 */
	private String genSubscribtionId(String appName, EventSubscription es) {
		String subscriptionId = appName + es.getMsgType() + es.getStaMacAddress();
		/*
		 * 当前所有订阅的注册是在程序开始进行的，从扩展性的角度考虑可能会在程序执行中订阅或者取消订阅
		 */
		scriptionsLock.readLock().lock();
		try {
			if (!subscriptions.containsKey(subscriptionId)) {
				return subscriptionId;
			}
		} finally {
			scriptionsLock.readLock().unlock();
		}
		return null;
	}

	/*
	 * 注册一个订阅，将订阅加入到订阅表中
	 */
	@Override
	public String registerSubscription(String appName, final EventSubscription es, final NotificationCallback cb) {
		String subscriptionId = genSubscribtionId(appName, es);// 生成一个注册的ID
		log.info("registerSubscription subscriptionId is " + subscriptionId);
		if (subscriptionId != null) {
			SubscriptionCallbackTuple tup = new SubscriptionCallbackTuple();
			tup.es = es;
			tup.cb = cb;
			scriptionsLock.writeLock().lock();// 读写锁
			try {
				if (msg2Subids.containsKey(es.getMsgType())) { // 先添加到msg2Subids中
					msg2Subids.get(es.getMsgType()).add(subscriptionId);
				} else {// 不存在对应的键则同时插入键值和set
					Set<String> subscriptionIds = new HashSet<String>();
					subscriptionIds.add(subscriptionId);
					msg2Subids.put(es.getMsgType(), subscriptionIds);
				}
				subscriptions.put(subscriptionId, tup);// 存入订阅注册表中
			} finally {
				scriptionsLock.writeLock().unlock();// 保障解锁
			}
		}
		return subscriptionId;
	}

	/*
	 * 取消一个订阅
	 */
	@Override
	public void unRegisterSubscription(String msgType, String subscriptionId) {
		scriptionsLock.writeLock().lock();// 加锁
		try {
			if (msg2Subids.containsKey(msgType)) {
				msg2Subids.get(msgType).remove(subscriptionId);
				subscriptions.remove(subscriptionId);
			}
		} finally {
			scriptionsLock.writeLock().unlock();// 保障解锁
		}
	}

	// debug打印agent当前station在线状态
	public static void showAgentVapState(Agent agent) {
		Master.consolePrintf("\n-------------current state---------------");
		System.out.println("agent: " + agent.getIpAddress() + "/" + agent.getMacAddress() + " mode: " + agent.getMode());
		 
		//做为对比测试。
		System.out.println("\n*SUM: " + agent.getStaCountSum() + 
						" \tVAP: " + (agent.getStaCountSum() - agent.getStaCountIdle()) +
						" \tIDLE: " + agent.getStaCountIdle()+
				 		" \tONLINE: " + agent.getStaCountOnline());		
		
		//System.out.println("added station: ");
		Iterator<Entry<String, VapContainer>> it = agent.getVapContainerSet().entrySet().iterator();
		VapContainer vapContainer = null;
		int i = 1;//
		int onlineNum = 0;
		while (it.hasNext()) {
			vapContainer = it.next().getValue();
			System.out.println("(" + i + ")\t" + vapContainer.getStation().getMacAddress() + "  "
					+ vapContainer.getStatus() + " \t" + vapContainer.getSsid());
			i++;
			if (vapContainer.getStatus().equals(Master.ON_LINE)) {
				onlineNum++;
			}
		}
		
		System.out.println("\nSUM: " + agent.getStaCountSum() + 
							" \tVAP: " + (i - 1) + 
							" \tIDLE: " + ((32 - i + 1) < 0 ? 0:(32 - i + 1))+ 
							" \tONLINE: " + onlineNum);
		System.out.println("-----------------------------------------\n");
	}
	
	/**
	 * 在日志中输出所有的视图
	 */
	public void showStatisticsView() {
		log.info("-----------showStatisticsView start-----------------------");
		log.info("打印数据统计视图");
		Map<String, Agent> agentMap = agentManager.getAgents();
		int totalWorkingSta = 0;
		for (Entry<String, Agent> entry : agentMap.entrySet()) {
			log.info("agent mac " + entry.getValue().getMacAddress());
			int curWorkingSta = 0;
			//long sumUpTraffic = 0, sumDownTraffic = 0;
			for (Station station : entry.getValue().getStationSet()) {
				// Station station = vap.getStation();
				if (station != null && station.getStatus().equals(Master.ON_LINE)) {
					curWorkingSta++;
					//sumUpTraffic += station.getUpTraffic();
					//sumDownTraffic += station.getDownTraffic();
					log.info("station mac " + station.getMacAddress() + " upTraffic " + station.getUpTraffic()
							+ " downTraffic " + station.getDownTraffic() + " sumUpTraffic " + station.getSumUpTraffic()
							+ " sumDownTraffic " + station.getSumDownTraffic());
				}

			}
			totalWorkingSta += curWorkingSta;
			log.info("online station num " + curWorkingSta + " sumUpTraffic " + entry.getValue().getSumUpTraffic()
					+ " sumDownTraffic " + entry.getValue().getSumDownTraffic());
		}
		log.info("total online station num " + totalWorkingSta);
		log.info("-------------showStatisticsView end---------------------");
	}

	public void showAllStoreView() {
		Map<String, Agent> agentMap = agentManager.getAgents();
		Map<SocketChannel, Agent> scAgentMap = agentManager.getScAgents();
		Map<String, Station> stationMap = stationManager.getStations();
		// Map<String, Vap> vapMap = vapManager.getVaps();
		Map<String, SendMsg> sendMsgMap = sendMsgBuffer.getSendMsgBuffer();
		log.info("----------------------------------");
		log.info("打印全局视图");
		log.info("AgentMap：");
		for (Entry<String, Agent> entry : agentMap.entrySet()) {
			log.info(entry.getValue());
			for (VapContainer vapContainer : entry.getValue().getVapContainerSet().values()) {
				log.info(vapContainer);

			}
		}

		log.info("AgentSocketMap：");
		for (Entry<SocketChannel, Agent> entry : scAgentMap.entrySet()) {
			log.info("sc：" + entry.getKey() + " " + entry.getValue());
		}

		log.info("StationMap：");
		log.info("---------------------------------------------------");
		for (Entry<String, Station> entry : stationMap.entrySet()) {
			log.info(entry.getValue());
			// Master.consolePrintf(entry.getValue());
			for (VapContainer vapContainer : entry.getValue().getVapContainerSet().values()) {
				log.info(vapContainer);
				// Master.consolePrintf(vapContainer);
			}
		}

		/*
		 * log.info("VapMap："); for (Entry<String, Vap> entry :
		 * vapMap.entrySet()) { log.info(entry.getValue()); }
		 */

		log.info("SendMsgMap：");
		for (Entry<String, SendMsg> entry : sendMsgMap.entrySet()) {
			log.info("sendMsgId：" + entry.getKey() + " " + entry.getValue());
		}
		log.info("----------------------------------");
	}

	/**
	 * 更新Agent的心跳信息
	 */
	public void updateAgentHeard(SocketChannel sc) {
		Agent agent = agentManager.getAgentBySc(sc);
		if (agent != null) {
			agent.setLastHeard(System.currentTimeMillis());
		}
	}

	/**
	 * 处理agent发送的Mac地址的消息，FNII_MSG_LOCAL_MAC：更新相应视图
	 */
	public void processMsgLocalMac(SocketChannel sc, String message, int msgLen) {
		log.info("processMsgLocalMac message is " + message);
		// Master.consolePrintf("processMsgLocalMac message is " + message);

		/* 消息解析 */
		int curPos = Master.MSG_ID_LEN + Master.MSG_TYPE_LEN + Master.MSG_LEN_LEN;
		int agentMacAddrLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
		String agentMacAddr = message.substring(curPos, (curPos += agentMacAddrLen));
		Agent agent = agentManager.getAgent(agentMacAddr);
		if (agent != null) {
			SocketChannel agentSc = agent.getSc();
			if (agentSc != null && agentSc != sc) {
				// 之前的连接还没有清除，需要清除

				try {// debug
					log.info("old socketsc is " + agentSc.getRemoteAddress() + "\n new sc is " + sc.getRemoteAddress());
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} // debug
				log.info("processMsgLocalMac :agentSc != null && agentSc != sc, start to close the socket..");
				closeSocket(agentSc);
				//return;
			}
		}
		// 新建agent
		agent = new Agent();
		agent.setMacAddress(agentMacAddr);
		try {
			agent.setSocketChannel(sc);
			agent.setIpAddress(((InetSocketAddress) sc.getRemoteAddress()).getAddress());
			agent.setLastHeard(System.currentTimeMillis());

			/*
			 * 假如在执行此处代码时，客户端突然断开，监听线程可能会调用（异常）closeSocket，下述代码可能在执行完
			 * closeSocket后执行， 导致不一致， 因此要同步
			 */
			synchronized (sc) {// 与closeSocket同步
				if (socketManager.isTracked(sc)) {// 没有被closeSocket
					// 存入全局视图
					agentManager.addAgent(agentMacAddr, agent);
					agentManager.putAgentBySc(sc, agent);
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			log.error(e.getMessage());
		}
	}

	/**
	 * 处理心跳消息，FNII_MSG_HEART_BEAT：更新心跳并回复确认
	 */
	public void processMsgHeartBeat(SocketChannel sc, String message, int msgLen) {
		if (message == null || sc == null || !agentManager.isTrackedSc(sc)) {
			log.info("!!MsgHeartBeat error! msg = " + message + " sc = " + sc + "!agentManager.isTrackedSc(sc): "
					+ !agentManager.isTrackedSc(sc));
			return;
		}

		try {
			log.info("processMsgHeartBeat agent ip is " + sc.getRemoteAddress().toString() + " message is " + message);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		// 本地维护心跳记录，在reset时查看是否超时，帮助定位是否因为心跳超时导致的reset
		Agent agent = agentManager.getAgentBySc(sc);
		agent.setLastHeartBeat(System.currentTimeMillis());

		writeMessage(sc, message + delimiter);// 回复确认
	}

	/**
	 * 处理 station上线时的消息，FNII_MSG_NEW_STA：更新相应视图
	 * 
	 * 注：此消息中，ap提供的vapMac不可用。
	 */
	public void processMsgNewStation(String message, SocketChannel sc, int msgLen) {
		if (message == null || sc == null || !agentManager.isTrackedSc(sc)) {
			return;
		}
		try {
			Agent agent = agentManager.getAgentBySc(sc);			
			/* 消息解析 */
			int curPos = 0;
			String ssid = null;
			String msgId = message.substring(curPos, (curPos += MSG_ID_LEN));
			curPos += (MSG_TYPE_LEN + MSG_LEN_LEN);
			int staMacLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
			String staMacAddr = message.substring(curPos, (curPos += staMacLen));
			int agentLen= Integer.parseInt(message.substring(curPos, (curPos += 2)));
			String agentMac = message.substring(curPos, (curPos += agentLen));//未用
			VapContainer vapContainer = null;
			
			if (agent != null) {
				log.info("processMsgNewStation message is " + message + " agent ip is " + agent.getIpAddress()
							+ " station mac is " + staMacAddr);
				if(message.length() <= MSG_NEWSTA_NOSSID_LEN){//单ssid版本(apl老版本)
					ssid = agent.getSsidSet().iterator().next();// 单ssid版本。多ssid版本由消息解析出ssid值。					
				}else{//多ssid版本
					int ssidLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
					ssid = message.substring(curPos, (curPos += ssidLen));	
					Master.consolePrintf("MultiSsid version.NewStationMsg: " + message+ " ssid: " + ssid);					
					 //检查上报的ssid是否正确
					if(!agent.getSsidSet().contains(ssid)){
						log.error("Agent :" + agent.getIpAddress()+ "/" + agent.getMacAddress() + 
									" Do not contains the ssid: " + ssid);
						Master.consolePrintf("error!!Agent :" + agent.getIpAddress()+ "/" + agent.getMacAddress() + 
									" Do not contains the ssid: " + ssid);
						return;
					}
				}
				
				Station station = stationManager.getStation(staMacAddr);
				if (station != null && station.getAgentSet().contains(agent)
						&& agent.getStationSet().contains(station)) {
					synchronized (station) {// 与closeSocket同步
						if (station.getStatus().equals(Master.ON_LINE)) {
							station.setLastHeard(System.currentTimeMillis());// 当前已经在线，更新station和vap的心跳
							station.setLastHeardMode(agent.getMode());
							if (station.getWorkingVapContainer().getAgent() != agent) {// 已在线终端，在另外一个agent上线处理。
								vapContainer = station.getVapContainer(agent, ssid);
								if (vapContainer != null) {
									vapContainer.setStatus(Master.ON_LINE);
									vapContainer.setLastProbeHeard(System.currentTimeMillis());
									station.setWorkingVapContainer(vapContainer);
									station.updateSumTraffic();
									station.setStatus(ON_LINE);
								} else {
									vapContainer = new VapContainer(station, agent, ssid);
									vapContainer.setLastProbeHeard(System.currentTimeMillis());
									vapContainer.setStatus(Master.ON_LINE);
									station.addVapContainer(vapContainer);
									station.setWorkingVapContainer(vapContainer);
									synchronized (agent) {
										agent.addVapContainer(vapContainer);
									}
								}
							}
						} else {
							Master.consolePrintf("NewStation: station offline.....");
							vapContainer = station.getVapContainer(agent, ssid);
							if (vapContainer == null) {
								Master.consolePrintf("Vap null, creat new vap: " + agent.getIpAddress()+"/"
													+ agent.getMacAddress()+ "/" + agent.getMode() + "-"
													+ ssid + "-"+ station.getMacAddress());
								vapContainer = new VapContainer(station, agent, ssid);//多ssid时，需要新建
							}
							
							// station不在线时，更新状态
							if (station.getAgentSet().contains(agent)) {// 更新状态等信息
								station.setLastHeard(System.currentTimeMillis());// 此时为在线状态下的心跳
								station.setLastHeardMode(agent.getMode());								
								vapContainer.setStatus(Master.ON_LINE);
								vapContainer.setLastProbeHeard(System.currentTimeMillis());
								station.setWorkingVapContainer(vapContainer);
								station.updateSumTraffic();
								// Master.consolePrintf("------setOnline-------------processMsgNewStation-2"
								// + "mode: "
								// + agent.getMode() + " " +
								// station.getMacAddress());
								station.setStatus(ON_LINE);
							}
						}
					}
				} else {
					if (station == null) {// 针对iphone手机，上送newstation消息时，强制直接上线！！
						station = new Station(staMacAddr, Master.ON_LINE); // working
						vapContainer = new VapContainer(station, agent, ssid);
						vapContainer.setStatus(ON_LINE);
						vapContainer.setLastProbeHeard(System.currentTimeMillis());
						station.setWorkingVapContainer(vapContainer);

						synchronized (agent) {
							station.addVapContainer(vapContainer);
							agent.addVapContainer(vapContainer);
						}
						//Master.consolePrintf("station == null");
						stationManager.addStation(station);
					}

					synchronized (station) {
						if (null == station.getVapContainer(agent, ssid)) {
							//Master.consolePrintf("stationVap == null, ");
							VapContainer vapContainerAgent = agent.getVapContainer(station, ssid);
							// Master.consolePrintf("vapContainerAgent: "+
							// vapContainerAgent);
							if (vapContainerAgent == null) {
								vapContainer = new VapContainer(station, agent, ssid);
								vapContainer.setStatus(ON_LINE);
								vapContainer.setLastProbeHeard(System.currentTimeMillis());
								station.addVapContainer(vapContainer);
								station.setWorkingVapContainer(vapContainer);
								synchronized (agent) {
									agent.addVapContainer(vapContainer);
								}
							} else {
								vapContainerAgent.setStatus(Master.ON_LINE);
								vapContainerAgent.setLastProbeHeard(System.currentTimeMillis());
								station.addVapContainer(vapContainerAgent);
								station.setWorkingVapContainer(vapContainerAgent);
							}
						}

						Master.consolePrintf("newStaMsg, setWorking ssid: " + ssid + " " + station.getMacAddress()
											+ " from Agent: " + agent.getIpAddress() + "/" + agent.getMacAddress()
											+ " stationWorkingSsid: " + station.getWorkingSsid());
						station.setLastHeard(System.currentTimeMillis());
						station.setLastHeardMode(agent.getMode());
					}
				}

				// 删除其他关联的vap
				for (VapContainer offVapContainer : station.getVapContainerSet().values()) {
					if (offVapContainer.getAgent() != agent && offVapContainer.getSsid().equals(ssid)) {
						log.info("station mac " + station.getMacAddress() + " is online and del other vap:"
								+ agent.getMacAddress());						
						delVapContainer(null, offVapContainer.getAgent(), station, offVapContainer.getSsid());
					}
				}
				StaCtrlMessage msg = new StaCtrlMessage(msgId, Message.FNII_MSG_NEW_STA, msgLen, staMacAddr, agent);
				// 检查是否发布消息
				checkSubscribtion(msg, staMacAddr);
			}

			Master.consolePrintf("NewStation: " + staMacAddr +", agent:"+ agent.getIpAddress()+"/"+agent.getMode());
			showAgentVapState(agent);// debug函数
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e.getMessage());
		}
	}

	/**
	 * 处理station下线时的消息，FNII_MSG_DEL_STA：更新相应视图 注：此消息中，ap提供的vapMac不可用。
	 */
	public void processMsgDelStation(String message, SocketChannel sc, int msgLen) {
		if (message == null || sc == null || !agentManager.isTrackedSc(sc)) {
			return;
		}
		try {/* 消息解析 */
			int curPos = 0;
			String ssid = null;
			curPos += MSG_ID_LEN;
			curPos += (MSG_TYPE_LEN + MSG_LEN_LEN);
			int staMacLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
			String staMacAddr = message.substring(curPos, (curPos += staMacLen));
			int agentLen= Integer.parseInt(message.substring(curPos, (curPos += 2)));
			String agentMac = message.substring(curPos, (curPos += agentLen));//未用
			Agent agent = agentManager.getAgentBySc(sc);
			Station station = stationManager.getStation(staMacAddr);								
			
			if (agent != null && station != null) {								
				if(message.length() <= MSG_DELSTA_NOSSID_LEN)//单ssid版本 
				{
					ssid = agent.getSsidSet().iterator().next();
				}else{//多ssid版本
					int ssidLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
					ssid = message.substring(curPos, (curPos += ssidLen));
					Master.consolePrintf("MultiSsid version.DelStaMsg : " + message + " ssid:" + ssid);	
					if(!agent.getSsidSet().contains(ssid)){
						log.error("Agent :" + agent.getIpAddress()+ "/" + agent.getMacAddress() + 
									" Do not contains the ssid: " + ssid);
						Master.consolePrintf("error!!!Agent :" + agent.getIpAddress()+ "/" + agent.getMacAddress() + 
									" Do not contains the ssid: " + ssid);
						return;
					}
				}
				
				log.info("processMsgDelStation message is " + message + " agent ip is " + agent.getIpAddress()
							+ "/" + ssid + " station mac is " + staMacAddr);	
				
				synchronized (station) {
					VapContainer vapContainer = station.getVapContainer(agent, ssid);
					if (vapContainer != null) {
						if (stationManager.isTracked(staMacAddr)) {// 更新状态等信息
							VapContainer workingvapContainer = station.getWorkingVapContainer();
							Master.consolePrintf("workingvapContainer == vapContainer :" + (workingvapContainer == vapContainer));
							if (workingvapContainer == vapContainer) {
								station.setStatus(Master.OFF_LINE);
								station.setWorkingVapContainer(null);
							}
							if (vapContainer.getStatus().equals(Master.ON_LINE)) {
								vapContainer.setStatus(OFF_LINE);
								synchronized (agent) {
									if (agentManager.isTracked(agent.getMacAddress())) {										
										Master.consolePrintf("processMsgDelStation : staMac:" + station.getMacAddress()
												+ "agent: " + agent.getIpAddress() + "/" + agent.getMacAddress() + "/" + ssid);
									}
								}
							}
						}
						vapContainer.setLastProbeHeard(System.currentTimeMillis());
					}
				}
				Master.consolePrintf("delStationMsg process finished.. agent: " 
						+ agent.getIpAddress() + "/" + agent.getMacAddress() + "/" + ssid + "/"+ agent.getMode()
						+ " station:" + staMacAddr + " stationWorkingSsid: " + station.getWorkingSsid());
			}


			showAgentVapState(agent);
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e.getMessage());
		}
	}

	/**
	 * 处理用户汇报station的流量统计信息，FNII_MSG_FLOW_INFO：更新相应视图
	 */
	public void processMsgFlowInfo(String message, SocketChannel sc, int msgLen) {
		if (message == null || sc == null || !agentManager.isTrackedSc(sc)) {
			return;
		}

		try {
			/* 消息解析 */
			int curPos = 0;
			curPos += MSG_ID_LEN;
			curPos += (MSG_TYPE_LEN + MSG_LEN_LEN);
			int staMacLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
			String staMacAddr = message.substring(curPos, (curPos += staMacLen));
			Station station = stationManager.getStation(staMacAddr);
			Agent agent = agentManager.getAgentBySc(sc);
			if (station != null && agent != null && station.getStatus().equals(Master.ON_LINE)) {
				// log.info("processMsgFlowInfo message is " + message + " agent
				// ip is " + agent.getIpAddress()
				// + " station mac is " + staMacAddr);

				synchronized (station) {//station上报的为流量总数
					if (station.getWorkingVapContainer().getAgent().getMacAddress().equals(agent.getMacAddress())) {
						// 此时agent上的vap不固定，可能会有多个ap上报同一个station的flow信息
						int upTrafficLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
						long upTraffic = Long.parseLong(message.substring(curPos, (curPos += upTrafficLen)));
						int downTrafficLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
						long downTraffic = Long.parseLong(message.substring(curPos, (curPos += downTrafficLen)));

						synchronized (agent) {// station同步
							if (stationManager.isTracked(staMacAddr) && station.getStatus().equals(Master.ON_LINE)) {
								// 更新流量信息
								//情况1.station重新上线
								if (station.getUpTraffic() > upTraffic || station.getDownTraffic() > downTraffic) {
									agent.addSumTraffic(upTraffic, downTraffic);
									station.updateSumTraffic();
								} else {//情况2.正常在线情况，把差额加上。
									agent.addSumTraffic(upTraffic - station.getUpTraffic(),
											downTraffic - station.getDownTraffic());
								}
							}
						}

						station.setDownTraffic(downTraffic);//更新流量
						station.setUpTraffic(upTraffic);						
						// station.setLastHeard(System.currentTimeMillis());
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
			log.error(e.getMessage());
		}
		// log.info(" processMsgFlowInfo end");
	}

	/**
	 * 处理汇报的station的probe帧信息，FNII_MSG_PROBE_INFO：更新相应视图
	 * 
	 */
	public void processMsgProbeInfo(String message, SocketChannel sc, int msgLen) {

		/*
		 * Master.consolePrintf("probe msg: " + message);
		 * //Master.consolePrintf(
		 * "----------------------probe start------------------------"); Date
		 * nowTimeStart = new Date(System.currentTimeMillis()); SimpleDateFormat
		 * sdFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS"); String
		 * retStrFormatNowDateStart = sdFormatter.format(nowTimeStart);
		 * Master.consolePrintf("probe start time: "+ retStrFormatNowDateStart);
		 */
		if (message == null || sc == null || !agentManager.isTrackedSc(sc)) {
			return;
		}
		try {
			int curPos = 0;
			String msgId = message.substring(curPos, (curPos += MSG_ID_LEN));
			curPos += (MSG_TYPE_LEN + MSG_LEN_LEN);
			int staMacLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
			String staMacAddr = message.substring(curPos, (curPos += staMacLen));
			int rssiLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
			int rssi = Integer.parseInt(message.substring(curPos, (curPos += rssiLen)));
			int auLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
			int au = Integer.parseInt(message.substring(curPos, (curPos += auLen)));
			int channelLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
			int channel = Integer.parseInt(message.substring(curPos, (curPos += channelLen)));
			 
			String mode = message.substring(curPos, (curPos += 3));
			Station station = stationManager.getStation(staMacAddr);

			if (station == null) {
				// 新建一个对应的station存入视图
				station = new Station(staMacAddr, Master.OFF_LINE);
				stationManager.addStation(station);
			}
			Agent agent = agentManager.getAgentBySc(sc);
			if (agent != null && agent.getStatus().equals(Master.ON_LINE)) {
				if (!station.getStatus().equals(Master.ON_LINE)) {
					// 只有在station不在线状态下才根据probe更新心跳
					if (rssi < Master.MIN_RSSI) {// 过滤station离线状态Rssi小于MIN_RSSI的消息
						return;
					}
				}

				String ssid = null;
				station.setLastHeard(System.currentTimeMillis());
				station.setLastHeardMode(agent.getMode());

				/* 多ssid模式 */
				Iterator<String> it = agent.getSsidSet().iterator();
				while (it.hasNext()) {
					ssid = it.next();
					VapContainer vapContainer = station.getVapContainer(agent, ssid);
					if (vapContainer != null) {
						vapContainer.setLastProbeHeard(System.currentTimeMillis());
					}
				}

				/*
				 * 将解析的内容构造成消息并检查本地订阅发布，如果有订阅则调用其对应回调函数特别注意的是对于probe信息我们需要
				 * 关注其对应的station当前是否处于上线状态
				 */
				
				ProbeInfoMessage msg = new ProbeInfoMessage(msgId, Message.FNII_MSG_PROBE_INFO, msgLen, staMacAddr,
						rssi,au, channel, mode, agent);
				checkSubscribtion(msg, staMacAddr);// 检查是否发布消息
			}
		} catch (Exception e) {
			log.error(e.getMessage());
			e.printStackTrace();
		}

		/*
		 * /for 性能测试 Date nowTimeEnd = new Date(System.currentTimeMillis());
		 * //SimpleDateFormat sdFormatter = new SimpleDateFormat(
		 * "yyyy-MM-dd HH:mm:ss"); String retStrFormatNowDateEnd =
		 * sdFormatter.format(nowTimeEnd); Master.consolePrintf(
		 * "probe end time: "+ retStrFormatNowDateEnd); //Master.consolePrintf(
		 * "----------------------probe end------------------------");
		 */

	}

	/**
	 * 处理命令执行成功的确认消息，FNII_MSG_OK：更新相应视图或执行相应命令，系统中的部分视图修改在命令执行成功后进行
	 */
	public void processMsgOk(String message, SocketChannel sc, int msgLen) {
		if (message == null || sc == null || !agentManager.isTrackedSc(sc)) {
			log.info("processMsgOk end for error.");
			return;
		}

		int curPos = 0;
		String msgId = message.substring(curPos, curPos += MSG_ID_LEN);
		Agent agent = agentManager.getAgentBySc(sc);
		if (agent != null) {
			log.info("processMsgok message is " + message + " agent is :" 
						+ agent.getIpAddress()+"/"+agent.getMacAddress()+"/"+agent.getMode());
			SendMsg sendMsg = sendMsgBuffer.getSendMsg(agent.getMacAddress() + msgId);
			if (sendMsg != null) {
				StationCtrlMessage vCMg = (StationCtrlMessage) sendMsg.getMsg();
				sendMsg.setStatus(SendMsgBuffer.MSG_OK);// 修改sendMsgBuffer中的状态为ok表示已经正确执行
				String ssid = vCMg.getSsid();
				switch (sendMsg.getMsg().getMsgType()) {
				case Message.FNII_MSG_ADD_VAP_ROAM: {// 执行漫游切换,addVap命令成功后，执行delVap命令//注意，ap不感知此类型，只把原msg返还给控制器
					HandoffMessage msg = (HandoffMessage) vCMg;
					Agent fromAgent = agentManager.getAgent(msg.getFromAgentMacAddr());
					Station station = stationManager.getStation(vCMg.getStaMacAddr());
					VapContainer vapContainer = station.getVapContainer(fromAgent, msg.getSsid());
					if (station != null && vapContainer != null) {
						synchronized (station) {
							Master.consolePrintf("ForceRoam step(4/5). Del fromAgent for station: "+ station.getMacAddress()
									+ ", fromAgent: " + fromAgent.getIpAddress()+"/"+fromAgent.getMacAddress()+"/"+fromAgent.getMode()
									+ ", toAgent: " + agent.getIpAddress()+ "/"+ agent.getMacAddress()+"/"+agent.getMode());
							delVapContainer(Message.FNII_ROAM_TYPE_FORCE, fromAgent, station, vapContainer.getSsid());
							//station.removeVapContainer(vapContainer);强制漫游不删vap，防止终端不主动断开无法上网
							//synchronized (fromAgent) {
							//	fromAgent.removeVapContainer(vapContainer);
							//}
						}
					}

				}
					break;
				case Message.FNII_MSG_OFFLINE_VAP:
					Master.consolePrintf("---Rcv msgOK of OFFLINE_VAP from " 
							+ agent.getIpAddress()+"/"+agent.getMode());
					break;
				case Message.FNII_MSG_ADD_VAP:
					break;

				case Message.FNII_MSG_DEL_VAP: {// 执行delVap命令成功后更新相应的视图
					Station station = stationManager.getStation(vCMg.getStaMacAddr());
					if (station != null) {
						synchronized (station) {
							if (stationManager.isTracked(station.getMacAddress())
									&& station.getAgentSet().contains(agent)) {
								VapContainer vapContainer = station.getVapContainer(agent, ssid);
								if(vapContainer != null){
									if (station.getVapContainer(agent, ssid).getStatus().equals(ON_LINE)) {// 当前关联的vap
										station.setStatus(Master.OFF_LINE);
										station.getVapContainer(agent, ssid).setStatus(OFF_LINE);
										if(station.getWorkingVapContainer().equals(vapContainer)){
											Master.consolePrintf("!!!ok, workingVap is the removed vapA.");
											station.setWorkingVapContainer(null);
										}else{
											//System.out.println("!!!error...");
										}
										
										Master.consolePrintf("processMsgOk-MSG_DEL_VAP will set workingVap null.station: " 
												+ station.getMacAddress() + ", workingVap'agent is:"+ station.getWorkingVapContainer().getAgent().getIpAddress()
												+ ", removed vap's agent is :"+ vapContainer.getAgent().getIpAddress()
												+"");										
									} else {
										// 非关联的vap，只在size为0时，将station状态置为off
										if (station.getAgentSet().size() == 0) {
											station.setStatus(Master.OFF_LINE);
											station.getVapContainer(agent, ssid).setStatus(OFF_LINE);
										}
									}
									synchronized (agent) {// 同步
										if (agentManager.isTracked(agent.getMacAddress())
												&& (agent.getStatus().equals(Master.ON_LINE))) {										
											agent.removeVapContainer(vapContainer);
											station.removeVapContainer(vapContainer);
											Master.consolePrintf(
													"processMsgOk-MSG_DEL_VAP : staMac:" + station.getMacAddress() 
													+ ",agent:"+ agent.getIpAddress()+ "/" + agent.getMode()
															+ " stationWorkingSsid: " + station.getWorkingSsid());
										}
									}
								}else{
									System.out.println("Vap not existed now, may be deleted.");
								}							
							}
						}
					}
				}
					break;
				default:
					break;
				}
			}
		}
	}

	/**
	 * 处理AP汇报的mode、channel，ssid等信息的消息，FNII_MSG_INTERFACE_NUM：设置agent的相应属性
	 */
	public void processMsgInterfaceNum(String message, SocketChannel sc, int msgLen) {
		// TODO Auto-generated method stub
		log.info(" processMsgInterfaceNum start");
		Master.consolePrintf("processMsgInterfaceNum: " + message);
		if (message == null || sc == null || !agentManager.isTrackedSc(sc)) {
			return;
		}
		try {
			log.info(message);
			/* 消息解析 */
			String BSSID = "ffffffffffff";
			int curPos = 0;
			curPos += MSG_ID_LEN;
			curPos += (MSG_TYPE_LEN + MSG_LEN_LEN);
			int staSizeLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
			int totStaSize = Integer.parseInt(message.substring(curPos, (curPos += staSizeLen)));
			int chaLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
			int channel = Integer.parseInt(message.substring(curPos, (curPos += chaLen)));
			String mode = message.substring(curPos, (curPos += 3));
			Agent agent = agentManager.getAgentBySc(sc);
			if (agent != null) {
				log.info("processMsgIFInfo message is " + message + " agent ip is " + agent.getIpAddress());
				// Master.consolePrintf("processMsgIFInfo message is " + message
				// + " agent ip is " + agent.getIpAddress());
				synchronized (agent) {
					agent.setStaCountSum(totStaSize);
					agent.setStaCountIdle(totStaSize);
					agent.setMode(mode);
					agent.setChannel(channel);
					agent.addBSSIDSet(BSSID);
					while (curPos < message.length()) {// ssid列表
						int ssidLen = Integer.parseInt(message.substring(curPos, (curPos += 2)));
						String ssid = message.substring(curPos, (curPos += ssidLen));
						agent.addSsidSet(ssid);
					}
				}
			}
		} catch (Exception e) {
			log.info(e);
			e.printStackTrace();
		}
	}

	/**
	 * 处理命令执行失败的确认消息，FNII_MSG_ERR：设置消息执行状态
	 */
	public void processMsgErr(String message, SocketChannel sc, int msgLen) {
		if (message == null || sc == null || !agentManager.isTrackedSc(sc)) {
			return;
		}
		/* 消息解析 */
		int curPos = 0;
		String msgId = message.substring(curPos, curPos += MSG_ID_LEN);
		Agent agent = agentManager.getAgentBySc(sc);
		if (agent != null) {
			log.info("processMsgerr message is " + message + " agent ip is " + agent.getIpAddress());
			SendMsg sendMsg = sendMsgBuffer.getSendMsg(agent.getMacAddress() + msgId);
			if (sendMsg != null) {
				sendMsg.setStatus(SendMsgBuffer.MSG_ERR);// 修改sendMsgBuffer中的状态为err表示没有正确执行
			}
		}
	}

	/**
	 * 关闭socket，清除此socket相关的所有视图信息，socketManager,agentManager,agent
	 */
	public void closeSocket(SocketChannel sc) {
		synchronized (sc) {
			try {
				log.info("!!closeSocket , remote is: " + sc.getRemoteAddress()
				 	+", local is: " + sc.getLocalAddress());

				if (socketManager.isTracked(sc)) {
					log.info("removeSocket in socketMap");
					socketManager.cancleKey(sc);
					socketManager.removeSocket(sc);
				}
				if (agentManager.isTrackedSc(sc)) {
					log.info("removeAgent in agentMap");
					Agent agent = agentManager.getAgentBySc(sc);
					if (agent != null) {
						showStatisticsView();// 关闭前打印一下流量信息
						log.info("Agent reset! agent : " + agent.getIpAddress() + "/" + agent.getMacAddress() + "/"
								+ agent.getMode() + ", sc remote: " + sc.getRemoteAddress());
						agent.reset(Master.getInstance());
						agentManager.removeAgentBySc(sc);
						agentManager.removeAgent(agent.getMacAddress());// 同时清除视图中的agent
					}
				}

				if (sc.isOpen()) {
					sc.close();
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				log.error(e);
			}
		}
	}

	/**
	 * 通过socketChannel向agent写对应命令
	 */
	private synchronized boolean writeMessage(SocketChannel sc, String message) {
		try {
			Agent agent = agentManager.getAgentBySc(sc);
			synchronized (sc) {// 与closeSocket中的sc.close同步
				if (sc.isOpen()) {					
					ByteBuffer writeBuf = ByteBuffer.allocate(MAX_MSG_LEN);
					writeBuf.clear();
					writeBuf.put(message.getBytes());
					writeBuf.flip();
					while (writeBuf.hasRemaining()) {
						sc.write(writeBuf);
					}
					log.info("writeMsg to Agent success: " + message + 
							", Agent:" + agent.getIpAddress()+ "/"+ agent.getMacAddress()+ "/"+ agent.getMode());
					return true;
				} else {
					log.info("!!error.socket not opened!!! "+ 
							", Agent:" + agent.getIpAddress()+ "/"+ agent.getMacAddress()+ "/"+ agent.getMode());
					Master.consolePrintf("!!error.socket not opened!!!"
										+ ", Agent:" + agent.getIpAddress()+ "/"+ agent.getMacAddress()+ "/"+ agent.getMode());
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			log.error(e.getMessage());
			return false;
		}
		return false;
	}

	/*
	 * 为上层应用提供的漫游切换函数，下发切换过程中的第一条命令addVap 强制漫游
	 */
	@Override
	public void handoffVap(Station station, Agent fromAgent, Agent toAgent, String ssid) {
		log.info("Force roam...HandoffAgent station mac is " + station.getMacAddress() + " source agentMac is "
				+ fromAgent.getMacAddress() + " dest agentMac is " + toAgent.getMacAddress());

		HandoffMessage addVapContainerMessage = new HandoffMessage(null, Message.FNII_MSG_ADD_VAP_ROAM, 0,
				station.getMacAddress(), fromAgent.getMacAddress(), ssid, toAgent.getMode(), toAgent);
		Master.consolePrintf("ForceRoam step(2/5) --excuteAddVap start: station:" + station.getMacAddress()
				+ ", from cAgent: " + fromAgent.getIpAddress()+ "/"+ fromAgent.getMacAddress()+"/"+fromAgent.getMode()
				+ ", to pAgent: " + toAgent.getIpAddress()+ "/" + toAgent.getMacAddress()+ "/"+ toAgent.getMode());
		excuteAddVap(true, addVapContainerMessage);
	}

	/*
	 * （1）为上层应用提供的接入控制中分配vap的函数，将需要执行的命令放入到cmdQueue队列中，支持未下发命令的更新	 
	 */
	@Override
	public void addVapContainer(boolean isNoForceRoam, Agent agent, String staMacAddress, String mode, String ssid) {
		// Master.consolePrintf("addVap! "+ ssid);
		log.info("APP addVap " + staMacAddress + " " + mode+" "+agent.toString());
		StationCtrlMessage VapCtlMsg;
		AppCmd appCmd;
		String key = staMacAddress + ssid;
		if (isNoForceRoam) {// 用于非强制漫游的addVap调用
			// log.info("roam");
			Master.consolePrintf("NotForce roam, start to add vap to pAgent: " 
					+ agent.getIpAddress()+"/"+agent.getMacAddress()+"/"+agent.getMode());
			VapCtlMsg = new StationCtrlMessage(null, Message.FNII_MSG_ADD_VAP, 0, staMacAddress, ssid, mode, agent);
			excuteAddVap(true, VapCtlMsg);
		} else {// 用于接入控制的addVap调用
			synchronized (staMacAddress) {// 加锁，防止同一个staMacAddr同时执行
				// 首次添加
				if (!cmdQueue.containsKey(key)) {
					VapCtlMsg = new StationCtrlMessage(null, Message.FNII_MSG_ADD_VAP, 0, staMacAddress, ssid, mode,
							agent);
					appCmd = new AppCmd(VapCtlMsg, System.currentTimeMillis());
					cmdQueue.put(key, appCmd);

				}
				// 再次添加时，需要实现5G对2.4G的替换
				else if (cmdQueue.get(key).message.getStaMacAddr().equals(staMacAddress)) {
					// Master.consolePrintf("!!!CmdQueue add msg, msgMode:
					// "+mode+
					// " oldMsgMode: "+ cmdQueue.get(key).message.getMode());
					if (!(cmdQueue.get(key).message.getMode().equals(Message.NET_5G)
							&& mode.equals(Message.NET_2_4G))) {
						// 只有2.4G的被替换
						VapCtlMsg = new StationCtrlMessage(null, Message.FNII_MSG_ADD_VAP, 0, staMacAddress, ssid, mode,
								agent);
						long heart = cmdQueue.get(key).firstHeart;
						appCmd = new AppCmd(VapCtlMsg, heart);
						cmdQueue.put(key, appCmd);
					}
				} else {
					Master.consolePrintf("add cmdQueue....3..");
				}
			}
		}
	}

	/*
	 * 如果已经给station下发过，不需要重复下发addAgent. 特殊情况：漫游时，需要重复下发 return: false需要下发
	 * ,true不需要下发
	 */
	private boolean checkSsidExist(boolean isRoam, Station station, Agent agent, String addSsid, String msgType) {
		boolean flag = false;
		Agent oldAgent = null;
		if (isRoam) {// 非强制/强制漫游add直接下发
			Iterator<Entry<String, VapContainer>> it = station.getVapContainerSet().entrySet().iterator();
			VapContainer vapContainer = null;
			while (it.hasNext()) {
				vapContainer = it.next().getValue();
				if (vapContainer.getStation() == station && vapContainer.getSsid().equals(addSsid)) {
					oldAgent = vapContainer.getAgent();
					if (oldAgent == agent) {// (1)相同vap不需要下发
						Master.consolePrintf("error, can't roam to itself!!");
						return true;
					}
				}
			}
			return false;
		} else {
			if (agent.getMode().equals(MODE_2d4G)) {
				if (station.getSsidSet().contains(addSsid)) {
					return true;
				}
			} else if (agent.getMode().equals(MODE_5G)) {
				if (station.getSsidSet().contains(addSsid)) {
					// 相同ssid，5G需要替换掉2.4G(oldAgent),同时(1)过滤掉相同的vap和(2)已存在5g的ssid的oldAgent
					Iterator<Entry<String, VapContainer>> it = station.getVapContainerSet().entrySet().iterator();
					VapContainer vapContainer = null;
					while (it.hasNext()) {
						vapContainer = it.next().getValue();
						if (vapContainer.getStation() == station && vapContainer.getSsid().equals(addSsid)) {
							oldAgent = vapContainer.getAgent();
							if (oldAgent == agent) {// (1)相同vap不需要下发
								return true;
							}
							if (oldAgent.getMode().equals(Master.MODE_5G)) {
								flag = true;
							}
						}
					}

					if (!flag) {
						// 把之前的2.4G删除掉，因为用户无法区分5g/2.4g的ssid
						// delVapContainer(oldAgent, station);//
						// 最好先下发，再删除。（可能有问题）
						return false;
					}
					return true;
				}
			}
			return false;
		}
	}

	/*
	 * 用于添加一个vap，此时采用预修改策略，即预先更新好station和vap的状态，相当于提前占用资源，
	 * 防止再分配给其他station，并下发命令给相应的AP
	 */
	private void excuteAddVap(boolean isRoam, StationCtrlMessage addStationMessage) {
		Agent agent = addStationMessage.getAgent();
		String msgType = addStationMessage.getMsgType();//roam or not-roam
		Master.consolePrintf(".......agent....: "+ agent);
		SocketChannel sc = agent.getSc();
		VapContainer vapContainer = null;
		if (sc != null && agentManager.isTrackedSc(sc)) {
			Station station = stationManager.getStation(addStationMessage.getStaMacAddr());
			if (station != null) {
				if (agent.getStatus().equals(ON_LINE)) {// agent必须在线
					synchronized (station) {
						String addSsid = addStationMessage.getSsid();
						boolean ssidExist = checkSsidExist(isRoam, station, agent, addSsid, msgType);// 相同的ssid,station只下发一次。

						// 2种情况下发vap:(1)station不存在对应的vap;(2)station对应的多个vap中，不存在ssid相同的vap.
						if (stationManager.isTracked(addStationMessage.getStaMacAddr())// 此时有可能station已经超时清除(ageTask)，因此判断
								&& !ssidExist) {
							// 创建vapContainer，保存agent/station/ssid的关联关系
							vapContainer = agent.getVapContainer(station, addSsid);							
							if (vapContainer == null) {
								vapContainer = new VapContainer(station, agent, addSsid);
								vapContainer.setStatus(OFF_LINE);
								vapContainer.setLastProbeHeard(System.currentTimeMillis());

								synchronized (agent) {
									if (agent.getStaCountIdle() < 0) {
										Master.consolePrintf("error!! idle：" + agent.getStaCountIdle());
									}
									agent.addVapContainer(vapContainer);
									station.addVapContainer(vapContainer);
								}
							} else {
								return;
							}

							executor.execute(new AgentStationAddRunnable(agent, station, addStationMessage));// 下发addVap命令
							log.info("addAgent execute finished. stationMac: " + station.getMacAddress() + " to Agent: "
									+ agent.getIpAddress() + "/" + agent.getMacAddress() + "/" + agent.getMode());
						}
					}
				} else {
					log.info("Agent is not online while excuteAddStation, start to close Socket..");
					closeSocket(agent.getSc());// 超时时主动closeSocket
				}
			}
		}
	}



	/*
	 * 用于删除一个vap，此时等到命令执行成功时才去更新station和vap的状态，防止因提前更改系统将vap预先分配
	 * 。并下发delVap的命令给AP
	 * isRoam: 只用于跟踪强制漫游定位。
	 */
	@Override
	public void delVapContainer(String roamType, Agent agent, Station station, String ssid) {
		log.info("delVap agent ip is " + agent.getIpAddress() + " station mac is " + station.getMacAddress());
		Master.consolePrintf("delVap, agent:" + agent.getIpAddress()+ "/" + agent.getMacAddress()+ "/"
				+ agent.getMode()+ "/"+ ssid + " station: " + station.getMacAddress());
		SocketChannel sc = agent.getSc();
		if (sc != null && agentManager.isTrackedSc(sc)) {
			if (agent.getStatus().equals(ON_LINE)) {// 先判断agent的状态
				if (station != null && station.getAgentSet().contains(agent)) {// station不能为空
					executor.execute(new AgentStationDelRunnable(roamType, agent, station, ssid));// 下发delVap命令
				}
			} else {
				log.info("delVapContainer failed, and close the socket.");
				closeSocket(agent.getSc());// 超时时主动closeSocket
			}
		}
	}

	/**
	 * 将元数据前补零，补后的总长度为指定的长度，以字符串的形式返回
	 */
	private String fillZero(int data, int totLen) {
		String newString = String.format("%0" + totLen + "d", data);
		return newString;
	}

	/**
	 * 启动定时任务AgeTask，CmdTask
	 * 
	 */

	private long lastTime = 0;

	public void startTimerTask() {
		timerExecutor.scheduleWithFixedDelay(new AgeTask(), 0, AGETASK_PERIOD, TimeUnit.SECONDS);
		timerExecutor.scheduleWithFixedDelay(new CmdTask(), 0, CMDTASK_PERIOD, TimeUnit.SECONDS);
		lastTime = System.currentTimeMillis();
	}

	/*
	 * CmdQueue的执行线程，对于接入控制应用，扫描命令队列CmdQueue，对于下发时间超过CMD_TIME_OUT的命令必须执行
	 */
	class CmdTask implements Runnable {
		@Override
		public void run() {
			// TODO Auto-generated method stub
			for (Entry<String, AppCmd> entry : cmdQueue.entrySet()) {
				AppCmd appCmd = entry.getValue();
				if (System.currentTimeMillis() - appCmd.firstHeart > CMD_TIME_OUT * 1000) {
					Station station = stationManager.getStation(appCmd.message.getStaMacAddr());
					if (station != null) {
						// 接入控制下发的，只有在offline时才执行，防止类似于iphone采用多个mac扫描时newStation后仍执行此add命令的情况
						if (stationManager.isTracked(appCmd.message.getStaMacAddr())) {
							excuteAddVap(false, appCmd.message);
						}
					}
					cmdQueue.remove(entry.getKey());
				}
			}
		}
	}

	/*
	 * iphone使用虚拟mac发送probe，因此需要对虚拟MAC单独老化 此函数为虚MAC检查函数,虚Mac格式如下：
	 * xx:xx:xx:xx:xx:xx 即：检查第1个字节的低第2位是否为1.
	 */
	static boolean isVirtualMac(String macAddress) {

		String stMacAddr2ndByte = macAddress.substring(1, 2);
		// Master.consolePrintf(stMacAddr2ndByte);
		if (stMacAddr2ndByte.equals("2") || stMacAddr2ndByte.equals("3") || stMacAddr2ndByte.equals("6")
				|| stMacAddr2ndByte.equals("7") || stMacAddr2ndByte.equals("10") || stMacAddr2ndByte.equals("11")
				|| stMacAddr2ndByte.equals("14") || stMacAddr2ndByte.equals("15")) {
			return true;
		}

		return false;
	}

	/*
	 * 老化线程，老化station，agent，vap
	 */
	class AgeTask implements Runnable {
		@Override
		public void run() {
			// TODO Auto-generated method stub
			try {
				if (System.currentTimeMillis() - lastTime > SHOW_TIME_OUT * 1000) {// 显示视图2分钟一次
					// 打印一次
					if (showStatisticsView) {
						showStatisticsView();
					}
					if (showStoreView) {
						showAllStoreView();
					}
					lastTime = System.currentTimeMillis();
				}

				/*
				 * 遍历station视图，对于在线的则检查其上次心跳是否超过STA_TIME_OUT秒，若是则置为下线
				 */
				for (Entry<String, Station> entry : stationManager.getStations().entrySet()) {
					Station station = entry.getValue();
					synchronized (station) {// 同步station
						if (station.getStatus().equals(Master.ON_LINE)) {
						} else {
							/*
							 * 对于下线的station，判断上次心跳是否超过AGE_TIME_OUT，
							 * 若是则认为其远离AP的范围， 故清空其vapSet， 并从视图去除station
							 * 首先检查是否iphone虚MAC,虚MAC老化时间为5min；
							 */
							if (System.currentTimeMillis() - station.getLastHeard() > AGE_TIME_OUT * 1000) {
								if (station.getVapContainerSet().size() > 0) {// 此时应清除station上的Vap
									for (VapContainer vapContainer : station.getVapContainerSet().values()) {
										if (vapContainer.getStation() == station) {
											Agent agent = vapContainer.getAgent();
											String ssid = vapContainer.getSsid();
											log.info("agetask: station " + station.getMacAddress()
													+ " is offline,del vap: " + agent.getMacAddress()+"/"+ssid+ "/" +station.getMacAddress());
											delVapContainer(null, agent, station, ssid);
											station.removeVapContainer(vapContainer);
											synchronized (agent) {
												agent.removeVapContainer(vapContainer);
											}
										}
									}
								} else {// 清除station，delVap只有在命令执行成功时才去修改视图，若在上面delVap后删除会出现delVap执行成功后发现station不在的情况
									stationManager.removeStation(station.getMacAddress());
								}
							} else {
								// 下线的并且station的上次心跳小于AGE_TIME_OUT
								/*
								 * 对于空闲的Vap进行老化
								 */
								Iterator<Entry<String, VapContainer>> it = station.getVapContainerSet().entrySet()
										.iterator();
								VapContainer vapContainer = null;
								while (it.hasNext()) {
									vapContainer = it.next().getValue();
									if (vapContainer.getStation() == station
											&& vapContainer.getStatus() == Master.OFF_LINE) {
										if ((isVirtualMac(station.getMacAddress()))
												&& (System.currentTimeMillis()
														- vapContainer.getLastProbeHeard() > VIRTUAL_STA_TIME_OUT //5min
																* 1000)
												|| System.currentTimeMillis()
														- vapContainer.getLastProbeHeard() > VAP_TIME_OUT * 1000) {//10min
											// 从此vap对应的AP收不到probe超过VAP_TIME_OUT
											Agent agent = vapContainer.getAgent();
											delVapContainer(null, agent, station, vapContainer.getSsid());
											agent.removeVapContainer(vapContainer);
											station.removeVapContainer(vapContainer);

											log.info("agetask: station " + station.getMacAddress()
													+ " is offline,vap lastProbeHeard > 60s,del vap "
													+ vapContainer.getAgent().getMacAddress());
										} else if (station.getLastHeard()
												- vapContainer.getLastProbeHeard() > INTVAL_TIME_OUT * 1000
												&& station.getLastHeardMode()
														.equals(vapContainer.getAgent().getMode())) {
											// 由于可能会遇到正在扫描的情况，station更新了时间戳，但是此vap还没有扫到，因此
											// 两次判断
											// Master.consolePrintf("---------Can
											// not be here...............");
											if (vapContainer.getLastProbeHeard() == vapContainer.getSavedProbeHeard()) {
												Agent agent = vapContainer.getAgent();
												delVapContainer(null, vapContainer.getAgent(), station,
														vapContainer.getSsid());
												log.info("agetask: station " + station.getMacAddress()
														+ " is offline,station.getLastHeard - vap.getLastProbeHeard > 12s,del vap"
														+ vapContainer.getAgent().getMacAddress());
												agent.removeVapContainer(vapContainer);
												station.removeVapContainer(vapContainer);

											} else {
												// 第一次记录此心跳
												vapContainer.setLastProbeHeard(station.getLastHeard());
												vapContainer.setSavedProbeHeard(vapContainer.getLastProbeHeard());
											}
										}
									}
								}
							}
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error("error" + e.getMessage());
			}
		}
	}

	/**
	 * 命令执行失败后取消先前的视图更改
	 */
	public void cancelTask(Message msg) {
		String staMacAddr = null;
		String agentMacAddr = null;
		SocketChannel sc = msg.getAgent().getSc();
		if (sc != null && agentManager.isTrackedSc(sc)) {
			switch (msg.getMsgType()) {
			case Message.FNII_MSG_ADD_VAP_ROAM:
			case Message.FNII_MSG_ADD_VAP:
				StationCtrlMessage addMsg = (StationCtrlMessage) msg;
				staMacAddr = addMsg.getStaMacAddr();
				agentMacAddr = msg.getAgent().getMacAddress();
				log.info("cancelTask addVap station mac is " + staMacAddr);

				// 如果执行了closeSocket此时不必执行下述代码
				Station station = stationManager.getStation(staMacAddr);
				Agent agent = agentManager.getAgent(agentMacAddr);
				if (station != null && agent != null) {
					synchronized (station) {// 修改station的状态，此时应同步
						if (stationManager.isTracked(staMacAddr) && station.getAgentSet().contains(station)) {
							// 回退
							synchronized (agent) {// agent 同步
								if (agentManager.isTracked(agentMacAddr) && agent.getStatus().equals(Master.ON_LINE)
										&& agent.getStationSet().contains(station)) {
									// 下发失败后，idle计数回收
									VapContainer vapContainer = station.getVapContainer(agent, addMsg.getSsid());
									agent.removeVapContainer(vapContainer);
									station.removeVapContainer(vapContainer);
								}
							}

							// station.removeVapContainer(agent,
							// addMsg.getSsid());
							if (station.getAgentSet().size() == 0 && station.getStatus() == Master.ON_LINE) {// 冗余保护代码
								station.setStatus(Master.OFF_LINE);
								station.getWorkingVapContainer().setStatus(Master.OFF_LINE);								
								Master.consolePrintf("cancelTask-FNII_MSG_ADD_VAP : staMac:" + station.getMacAddress());
										//+ " onlineCount: " + agent.getStaCountOnline());
								station.setWorkingVapContainer(null);
							}
						}
					}
				}

				break;
			case Message.FNII_MSG_DEL_VAP:
				/*
				 * 执行delVap命令失败并且agent离线状态则关闭连接，如果delVap命令由于超时被多次执行，
				 * 第一次执行成功，后续执行失败则不会出现问题
				 */
				if (msg.getAgent().getStatus().equals(Master.OFF_LINE)) {
					log.info("cancelTask delStation-msg, and start to close the socket..");
					closeSocket(sc);
				}

				break;

			default:
				break;
			}
		}
	}

	/**
	 * 消息检测定时器：在发送完成消息后同时启动，在超时时间之内检查消息执行的结果，做相应的处理
	 * 
	 */
	private class CheckSendMsgTask extends TimerTask {
		String sendMsgId;

		public CheckSendMsgTask(String sendMsgId) {
			this.sendMsgId = sendMsgId;
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			SendMsg sendMsg = sendMsgBuffer.getSendMsg(sendMsgId);
			switch (sendMsg.getStatus()) {
			case SendMsgBuffer.MSG_ERR:
				// 失败则cancelTask
				cancelTask(sendMsg.getMsg());
				break;
			case SendMsgBuffer.MSG_OK:
				sendMsgBuffer.removeSendMsg(sendMsgId);// 从消息发送缓冲中删除
				break;
			case SendMsgBuffer.MSG_UNREPLY:
				// 未回复则cancelTask
				cancelTask(sendMsg.getMsg());
				break;
			default:
				break;
			}
		}
	}

	/**
	 * 下发addVap命令的任务执行接口（接入控制和漫游中的addVap） ，对消息进行封装和调用socketChannel发送，
	 * 并将其存储到消息发送缓冲 注：vap废弃。直接下发station对应agent
	 */
	private class AgentStationAddRunnable implements Runnable {
		Agent agent;
		boolean isRoam;		
		Station station;
		StationCtrlMessage addStationMessage;

		AgentStationAddRunnable(Agent agent, Station station, StationCtrlMessage addStationMessage) {
			this.agent = agent;
			this.station = station;
			this.addStationMessage = addStationMessage;			
		}

		@Override
		public void run() {
			String staMacAddr = station.getMacAddress();
			String msgId = fillZero(agent.getMsgId(), 4);
			StringBuffer message = new StringBuffer(msgId);
			StringBuffer tmp = new StringBuffer(staMacAddr.length() + "");
			String addSsid = addStationMessage.getSsid();// 需要下发的ssid.
			String mode = addStationMessage.getMode(); // 2.4G or 5G
			String msgType = addStationMessage.getMsgType();
			// 暂时保留vap所占的空间。但内容无效。
			tmp.append(staMacAddr).append(staMacAddr.length()).append("ffffffffffff")
					.append(mode).append(fillZero(addSsid.length(), 2)).append(addSsid);
			/*
			 * 注:漫游时下发类型为FNII_MSG_ADD_VAP！！ap不需感知是漫游.VAP_ROAM消息保存在sendMsgBuffer中，当收到OK消息时，根据消息ID再从
			 * sendMsgBuffer中获取添加VAP类型是否为漫游。
			 * */
			message.append(Message.FNII_MSG_ADD_VAP).append(fillZero(tmp.length(), 3)).append(tmp).append(delimiter);
			SocketChannel sc = agent.getSc();

			if (sc != null && writeMessage(sc, message.toString())) {// 命令成功发送
				//station.addAddAgentMark(mode, false);// for debug
				if(msgType.equals(Message.FNII_MSG_ADD_VAP_ROAM)){
					Master.consolePrintf("ForceRoam step(3/5) ++Addvap to ap succ. "
							+ " agent: " + agent.getIpAddress() + "/" + agent.getMacAddress() + "/" + mode + "/" + addSsid
							+ " stationMac :" + station.getMacAddress());
				}else{
					Master.consolePrintf("++Addvap msg write to ap scucc: " 
							+ " agent: " + agent.getIpAddress() + "/" + agent.getMacAddress() + "/" + mode + "/" + addSsid
							+ " stationMac :" + station.getMacAddress());
				}				

				String agentVapMsg = "["+agent.getIpAddress()+"/"+agent.getMacAddress()+ "/" + mode + "/" + addSsid
								+ "/" + station.getMacAddress()+"] ";
				//log.info(agentMsg+"++Addvap msg write scucc: " 
				//		+ " agent: " + agent.getIpAddress() + "/" + agent.getMacAddress() + "/" + mode + "/" + addSsid
				//		+ " stationMac :" + station.getMacAddress());								
				log.info(agentVapMsg+ "Addvap msg write scucc: " + message.toString());
				addStationMessage.setMsgId(msgId);
				addStationMessage.setMsgLen(tmp.length());
				SendMsg sendMsg = new SendMsg(addStationMessage, SendMsgBuffer.MSG_UNREPLY);
				sendMsgBuffer.putSendMsg(agent.getMacAddress() + msgId, sendMsg);
				// 启动命令执行结果检测线程
				timerExecutor.schedule(new CheckSendMsgTask(agent.getMacAddress() + msgId), AGENT_TIME_OUT,
						TimeUnit.SECONDS);
			} else {// 如果write失败，执行cancelTask取消视图修改
				cancelTask(addStationMessage);
			}
		}
	}

	/**
	 * 下发delVap命令的任务执行接口（包含漫游切换和老化中的delVap),对消息进行封装和调用socketChannel发送，
	 * 并将其存储到消息发送缓冲
	 * 
	 */
	private class AgentStationDelRunnable implements Runnable {
		Agent agent;
		Station station;
		String ssid;
		String roamType;
		AgentStationDelRunnable(String roamType, Agent agent, Station station, String ssid) {
			this.agent = agent;
			this.station = station;
			this.ssid = ssid;
			this.roamType = roamType;
		}

		@Override
		public void run() {
			String staMacAddr = station.getMacAddress();
			String msgId = fillZero(agent.getMsgId(), 4);
			StringBuffer message = new StringBuffer(msgId);
			StringBuffer tmp = new StringBuffer(staMacAddr.length() + "");
			String msgType = Message.FNII_MSG_DEL_VAP; 
			if(roamType!=null &&(roamType.equals(Message.FNII_ROAM_TYPE_FORCE)||roamType.equals(Message.FNII_ROAM_TYPE_DELCAGENT))){
				msgType = Message.FNII_MSG_OFFLINE_VAP;//强制漫游时ap不删除原agent的vap，防止切换失败无法上网。
			}			
			
			tmp.append(staMacAddr).append(staMacAddr.length()).append("ffffffffffff")
					.append(agent.getMode()).append(fillZero(ssid.length(), 2)).append(ssid);// vap用全f代替			
			message.append(msgType).append(fillZero(tmp.length(), 3)).append(tmp).append(delimiter);
			SocketChannel sc = agent.getSc();
			StationCtrlMessage delVapContainerMessage = new StationCtrlMessage(msgId, msgType,
					tmp.length(), staMacAddr, ssid, null, agent);
			log.info("delAgentMessage: stationMac: " + staMacAddr + " " + delVapContainerMessage);
			if (sc != null && writeMessage(sc, message.toString())) {// 命令成功发送				
				if(this.roamType!=null&&this.roamType.equals(Message.FNII_ROAM_TYPE_FORCE)){
					Master.consolePrintf("ForceRoam step(5/5) --delVap succ for agent: "+ agent.getIpAddress() + "/" + agent.getMacAddress()
					+ "/" + agent.getMode() +"/" + ssid + " station: " + station.getMacAddress());
				}else{
					Master.consolePrintf("--delVap succ for agent: " + agent.getIpAddress() + "/" + agent.getMacAddress()
					+ "/" + agent.getMode() +"/" + ssid + " station: " + station.getMacAddress());
				}
				
				log.info("--delVap succ for agent: " + agent.getIpAddress() + "/" + agent.getMacAddress()
							+ "/" + agent.getMode() +"/" + ssid + " station: " + station.getMacAddress());
				// 缓冲中消息的ID 由消息的ID和agent的mac地址共同决定
				sendMsgBuffer.putSendMsg(agent.getMacAddress() + msgId,
						new SendMsg(delVapContainerMessage, SendMsgBuffer.MSG_UNREPLY));
				
				// 启动命令执行结果检测线程
				timerExecutor.schedule(new CheckSendMsgTask(agent.getMacAddress() + msgId), AGENT_TIME_OUT,
						TimeUnit.SECONDS);
			} else {// //如果write失败，执行cancelTask取消视图修改
				cancelTask(delVapContainerMessage);
			}
		}
	}

	public static void consolePrintf(Object x) {
		String s = String.valueOf(x);
		Date nowTime = new Date(System.currentTimeMillis());
		System.out.println(dateFormat.format(nowTime) + " " + s);
	}

	/* 私有构造方法，防止被实例化 */
	private Master() {
	}

	/* 此处使用一个内部类来维护单例 */
	private static class MasterFactory {
		private static Master instance = new Master();
	}

	/* 获取实例 */
	public static Master getInstance() {
		return MasterFactory.instance;
	}

	/* 如果该对象被用于序列化，可以保证对象在序列化前后保持一致 */
	public Object readResolve() {
		return getInstance();
	}

	/* get set方法 */
	@Override
	public Agent getAgent(String agentMacAddr) {
		// TODO Auto-generated method stub
		if (agentManager.isTracked(agentMacAddr)) {
			return agentManager.getAgent(agentMacAddr);
		}
		return null;
	}

	@Override
	public Station getStation(String staMacAddress) {
		if (stationManager.isTracked(staMacAddress)) {
			return stationManager.getStation(staMacAddress);
		}
		return null;
	}

	public Agent getAgentBySc(SocketChannel sc) {
		return agentManager.getAgentBySc(sc);

	}

	public ExecutorService getExecutor() {
		return executor;
	}

	public void putSocketInfo(SocketChannel sc, SocketInfo socketInfo) {
		socketManager.putSocketInfo(sc, socketInfo);
	}

	public SocketInfo getSocketInfo(SocketChannel sc) {
		return socketManager.getSocketInfo(sc);
	}

	public static StationManager getStationManager() {
		return stationManager;
	}

	public void setExecutor(ScheduledExecutorService executor) {
		this.executor = executor;
	}

	public void setStationManager(StationManager stationManager) {
		Master.stationManager = stationManager;
	}

	public AgentManager getAgentManager() {
		return agentManager;
	}

	public void setAgentManager(AgentManager agentManager) {
		this.agentManager = agentManager;
	}
}