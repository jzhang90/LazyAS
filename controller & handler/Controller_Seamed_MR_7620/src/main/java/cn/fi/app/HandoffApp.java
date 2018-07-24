package cn.fi.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Map.Entry;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cn.fi.main.Master;
import cn.fi.obj.Agent;
import cn.fi.obj.Application;
import cn.fi.obj.Station;
import cn.fi.obj.VapContainer;
//import cn.fi.obj.Vap;
import cn.fi.obj.msg.Message;
import cn.fi.obj.msg.ProbeInfoMessage;
import cn.fi.obj.sub.EventSubscription;
import cn.fi.obj.sub.NotificationCallback;
import cn.fi.obj.sub.NotificationCallbackContext;

public class HandoffApp extends Application {
	private String appName = "HandoffApp";
	private Log log = LogFactory.getLog(this.getClass());
	private int waitTimeout = 1200;// 120s一次删除
	private int handoffinterval = 5;// 切换的时间间隔
	private int queueRssiSize = 1;// 固定队列长度
	private int threshold1 = 10;//23;// 非强制阈值
	private int threshold2 = 20;//48;// 强制阈值
	private int allChannel = 26;
	// private int channelAlloc[] = new int[14];// 存储每个信道当前sta的数量
	private boolean forceRoamMark = false;// 是否强制切换
	private boolean noForceRoamMark = false;//是否非强制
	private boolean deCurrentApMark = false;// true为删除当前关联的cAgent
	private Date timeout = null;
	// 存储每个station对应的信道,所有AP的扫描情况,分配的信道,AP和时间
	private Hashtable<String, chaHandList[]> sta2ChannelScan = new Hashtable<String, chaHandList[]>();
	private Hashtable<String, Long> sta2DisTime = new Hashtable<String, Long>();// staMAC-Date
	private Hashtable<String, Agent> sta2DisedOldAgent = new Hashtable<String, Agent>();// 分配
	private Hashtable<String, Agent> sta2DisedNewAgent = new Hashtable<String, Agent>();
	// "38bc1aa7f1ca", "00664ba30773", "d46e5c0cc7f1", "98ffd033d582" };
	private String[] macAddrList = new String[100];
	private String macAddrFile = "macAddrList";
	private Agent currentAgent = null;
	private long time = 0;
	@Override
	public void run() {
		// TODO Auto-generated method stub
		init();
	}

	private int readList(String fileName, String[] strArray) {
		File file = new File(fileName);
		BufferedReader reader = null;
		int i = 0;
		try {
			reader = new BufferedReader(new FileReader(file));
			String tempStr = null;
			// 一次读一行，读入null时文件结束
			while ((tempStr = reader.readLine()) != null) {
				strArray[i++] = tempStr;
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e1) {
				}
			}
		}
		return i;
	}

	private void init() {
		log.info("init" + appName);
		// 847a880af2b0====380a94e4179e===9c35eb86dbe0==9cf3875c7412
		NotificationCallback cb = new NotificationCallback() {// 收到订阅的消息时 的回调函数
			@Override
			public void exec(EventSubscription es, NotificationCallbackContext cntx) {
				// log.info("NotificationCallback exec");
				handle(es, cntx);
			}
		};

		int length = readList(macAddrFile, macAddrList);
		for (int i = 0; i < length; i++) {
			EventSubscription es = new EventSubscription(macAddrList[i], Message.FNII_MSG_PROBE_INFO, Master.ON_LINE);
			registerSubscription(appName, es, cb);
		}
	}

	public int channeltoIndex(int channel) {
		int index = 0;
		if (channel < 14) {
			index = channel;
		} else if (channel < 65)// 36(14),40, 44, 48, 52, 56, 60, 64(21),
		{
			index = 5 + channel / 4;
		}
		// 149->22;165->22;
		else if (channel < 166)// 149(22),153, 157, 161, 165(26)
		{
			index = -15 + channel / 4;
		}
		return index;
	}

	//把handle开始检查异常的代码放在此处
	private boolean probeMsgCheck(ProbeInfoMessage msg){
		String testMac = "bc6c214e002a";// 12位
		String staMacAddress = msg.getStaMacAddr();
		String agentMacAddress = msg.getAgent().getMacAddress();
		Station station = this.getStation(staMacAddress);
		String workingSsid = station.getWorkingSsid();
		
		if (staMacAddress.length() != testMac.length()) {
			Master.consolePrintf("probe msg error!!! staMAC: " + staMacAddress);
			return false;
		}
		
		//Master.consolePrintf("-----handoff probe from agent: " + this.getAgent(agentMacAddress).getIpAddress()
		//		+"/"+ this.getAgent(agentMacAddress).getMode());				
		
		if (workingSsid == null || !this.getAgent(agentMacAddress).getSsidSet().contains(workingSsid)) {
			//Master.consolePrintf("-----return for ssid not exist, workingSsid: "+ workingSsid
			//		+ ", agent: "+this.getAgent(agentMacAddress).getIpAddress() + " has ssidset: " + this.getAgent(agentMacAddress).getSsidSet());
			return false;
		}				
		
		VapContainer workingVapContainer = this.getStation(staMacAddress).getWorkingVapContainer();		
		if (workingVapContainer != null) {
			this.currentAgent = workingVapContainer.getAgent();
		}		
		if (this.currentAgent == null) {
			Master.consolePrintf("-----return for cAgent null");
			return false;
		}		
		
		return true;
	}
	
	private synchronized void handle(EventSubscription oes, NotificationCallbackContext cntx) {
		ProbeInfoMessage msg = (ProbeInfoMessage) cntx.message;		
		String staMacAddress = msg.getStaMacAddr();
		String agentMacAddress = msg.getAgent().getMacAddress();
		int channel = msg.getChannel();
		//Agent currentAgent = null;
		Station station = this.getStation(staMacAddress);
		String workingSsid = station.getWorkingSsid();
		this.deCurrentApMark = false;
		this.forceRoamMark = false;
		this.noForceRoamMark = false;
		if(true != probeMsgCheck(msg)){
			return;
		}
									
		// 如果当前cAgent的STA和分配的new一样就就是add成功delete
		if (this.sta2DisedNewAgent.containsKey(staMacAddress)) {
			boolean roamOK = this.sta2DisedNewAgent.get(staMacAddress).getMacAddress()
					.equals(currentAgent.getMacAddress());//表示currentAgent为漫游目的Agent
			boolean addOK = this.getStation(staMacAddress).getAgentSet()
					.contains(this.sta2DisedNewAgent.get(staMacAddress));//station已添加vap到需要漫游的Agent上
			//Master.consolePrintf("roamOK: " + roamOK + " addOK: " + addOK);
			
			// 成功了且关联
			if (roamOK || !roamOK && !addOK) {
				// 如果当前cAgent的STA和分配的new一样就就是add成功delete
				this.sta2DisedNewAgent.remove(staMacAddress);  //??
			}
		}

		// 接管上限的Probe
		// 1.更新周围STA的信息；
		chaHandList[] staChaHandList = this.sta2ChannelScan.get(staMacAddress);
		if (staChaHandList == null) {
			staChaHandList = new chaHandList[allChannel + 1];
			this.sta2ChannelScan.put(staMacAddress, staChaHandList);			
		}		
		
		// 2.更新AP和RSSI	
		int channelIndex = this.channeltoIndex(channel);
		if (staChaHandList[channelIndex] == null) {
			staChaHandList[channelIndex] = new chaHandList(this.queueRssiSize);
		}
		
		staChaHandList[channelIndex].addAP(agentMacAddress, msg.getRssi(), channelIndex);			
		Master.consolePrintf("staChaHandList added apNode: agent: " + this.getAgent(agentMacAddress).getIpAddress()
				+"/"+ this.getAgent(agentMacAddress).getMode()+ "/"+ this.getAgent(agentMacAddress).getChannel()
				+ ", msgRssi:" + msg.getRssi());		
		
		//此判断必须放在addApp后面，用于添加capnode；
		if(this.currentAgent.getMacAddress().equals(agentMacAddress)){//过滤掉本agent的probe
			return;
		}	
		
		//此判断也放在addAp之后，防止无法添加node。
		if (this.sta2DisTime.get(staMacAddress) != null
				&& System.currentTimeMillis() - this.sta2DisTime.get(staMacAddress) < this.handoffinterval * 1000) {			
			Master.consolePrintf("-----return for 5s");
			return;
		}
		
		Master.consolePrintf("HANDOFF-probemsg rcved," + 
				" probeAgent: " + msg.getAgent().getIpAddress() +"/"+agentMacAddress+"/"+ msg.getMode()+ 
				" cAgent: "+this.currentAgent.getIpAddress() + "/" + this.currentAgent.getMacAddress() + 
				"/" + this.currentAgent.getMode() + " station: " + station.getMacAddress());

		this.sta2DisedOldAgent.put(staMacAddress, currentAgent);
		
		// 得到需要分配的新的VAP
		Agent pAgent = this.handOff(currentAgent, staChaHandList, agentMacAddress, 
									channel, staMacAddress, workingSsid);						
		if (pAgent != null) {
			// 检查pAgent是否包含有当前ssid
			if (!pAgent.getSsidSet().contains(workingSsid)) {				
				Master.consolePrintf("pAgent ssidset: " + pAgent.getSsidSet() + " NOT contains workingssid: " + workingSsid);
				return;
			}
			
			Master.consolePrintf("After check pmark and cmark, pAgent: " + pAgent.getIpAddress() + "/"+ pAgent.getMode()
					+ ", forceMark: " + this.forceRoamMark+ ", noForceMark: "+ this.noForceRoamMark+ ", deCapMark:" + this.deCurrentApMark
					+ ". Start checking wheather or not roaming");
			
			if (this.forceRoamMark == true) {//强制切换				
				//Date nowTime = new Date();
				//SimpleDateFormat t = new SimpleDateFormat("HH mm ss");
				Master.consolePrintf("===Roam type--1===, ForceRoam step(1/5) station: " + staMacAddress 
						+ " to:" + pAgent.getIpAddress() + "/"+ pAgent.getMacAddress() + "/" + pAgent.getMode()
						+ " from AP: " +currentAgent.getIpAddress()+"/"+ currentAgent.getMacAddress() + "/"+ currentAgent.getMode() 
						);
				
				this.handoffVap(this.getStation(staMacAddress), currentAgent, pAgent, workingSsid);
				this.sta2DisedNewAgent.remove(staMacAddress);
			} else if(this.noForceRoamMark == true) {//非强制
				//Date nowTime = new Date();
				//SimpleDateFormat t = new SimpleDateFormat("HH mm ss");
				Master.consolePrintf("===Roam type--2===, noForceRoam." + " STA:" + staMacAddress + 
						" to:" + pAgent.getIpAddress()+ "/" + pAgent.getMacAddress() + "-" + pAgent.getMode() +
						" from" + currentAgent.getIpAddress() + "/"+ currentAgent.getMacAddress() + "-" + currentAgent.getMode() + 								 
						" agentSetSize: "+ this.getStation(staMacAddress).getAgentSet().size());
				
				this.addVapContainer(true, pAgent, msg.getStaMacAddr(), msg.getMode(), workingSsid);//注:true,漫游下发vap
				this.sta2DisedNewAgent.put(staMacAddress, pAgent);
			}
		} else{// 如果上次是非强制，这次是强制,非强制时已下发过vap，此时只需删除当前cAP上的vap。
			//Master.consolePrintf("pAgent == null, maybe vap has been added to probe-Agent:"+ msg.getAgent().getIpAddress());
			//强制，且已给pAgent下发过vap
			if (this.forceRoamMark == true && this.deCurrentApMark == true) {
				Master.consolePrintf("===Roam type--3=== forceRoamMark/deCurrentApMark all had been set true, pAgent"
					+" has the vap , cAgent: "+currentAgent.getIpAddress()+"/"+currentAgent.getMode()+" will offline the vap");
				//VapContainer vapContainer = currentAgent.getVapContainer(station, workingSsid);
				this.delVapContainer(Message.FNII_ROAM_TYPE_DELCAGENT, currentAgent, station, workingSsid);
				//currentAgent.removeVapContainer(vapContainer);//强制漫游不删除vap，防止终端不能正确解除关联导致无法上网
				//station.removeVapContainer(vapContainer);
			}
		}

		//记录每次的漫游时间，handoff模块5s之内不再漫游，即5s不接收probe。
		this.time = System.currentTimeMillis();
		this.sta2DisTime.put(staMacAddress, this.time);	
		Master.consolePrintf("-----handoff finished. And record the time.");
		
		// 3.维护保留的扫描信息，超时的STA删除，超时AP的删除
		if (timeout == null || (timeout != null && this.time - timeout.getTime() > this.waitTimeout * 100)) {
			timeout = new Date();
			Iterator<String> itStaMac = this.sta2ChannelScan.keySet().iterator();
			while (itStaMac.hasNext()) {
				String staMAC = itStaMac.next();
				boolean allChadel = true;
				for (int i = 1; i < allChannel; i++) {
					if (this.sta2ChannelScan.get(staMAC) == null) {
						continue;
					}
					chaHandList staChannelList = this.sta2ChannelScan.get(staMAC)[i];
					long dtime;
					if (staChannelList == null) {
						dtime = this.time + this.waitTimeout * 1000 - 1;
					} else {
						dtime = staChannelList.date.getTime();
					}
					if (dtime - this.time > this.waitTimeout * 1000) {
						Iterator<Map.Entry<String, apHandNode>> itApSet = staChannelList.list.entrySet().iterator();
						while (itApSet.hasNext()) {
							Map.Entry<String, apHandNode> apEntry = itApSet.next();
							apHandNode ap = apEntry.getValue();
							if (this.time - ap.new_date > this.waitTimeout * 1000) {
								itApSet.remove();
							}
						}
					} else {
						allChadel = false;
					}
				}
				
				if (allChadel == true) {
					itStaMac.remove();
					this.sta2DisedNewAgent.remove(staMAC);
					this.sta2DisedOldAgent.remove(staMAC);
					this.sta2DisTime.remove(msg.getStaMacAddr());
				}
			}
		}
	}

	// 找一个信号最强的，且有空余的VAP
	public Agent handOff(Agent cAgent, chaHandList[] ChaHandList, String tAgentMacAddr, int tchannel,
							String staMacAddress, String workingSsid) {
		chaHandList staChaHandList[] = ChaHandList;
		String cAgentMacAddr = cAgent.getMacAddress();
		int channel = cAgent.getChannel();// 当前频段
		Station station = this.getStation(staMacAddress);
		Agent pAgent = this.getAgent(tAgentMacAddr);		
		
		int channelIndex = chaList.channeltoIndex(channel);
		int tchannelIndex = chaList.channeltoIndex(tchannel);
		if (staChaHandList[channelIndex] == null) {
			Master.consolePrintf("staChaHandList[channelIndex] is null , channel: " + channel);
			return null;
		}

		Master.consolePrintf("before Handoff check, pAgent: " + pAgent.getIpAddress() + "/"+ pAgent.getMode()+ "channelIndex : " + channelIndex + " tchannelIndex: "+ tchannelIndex
				+ ", forceMark:"+ this.forceRoamMark+ ", noforceMark: " + this.noForceRoamMark
				+ ", deCagentMark: " + this.deCurrentApMark);
		// 只看当前的AP和关联的AP（PVAP和cAgent）
		apHandNode cApNode = staChaHandList[channelIndex].getApNodeByApMac(cAgentMacAddr);
		apHandNode tApNode = staChaHandList[tchannelIndex].getApNodeByApMac(tAgentMacAddr);
		if (cApNode == null || tApNode == null) {
			Master.consolePrintf("cap == null : "+ (cApNode == null) +" || tap == null : " + (tApNode == null)
					+ ", if has one null , quit." + " channelIndex: " + channelIndex + " cAgent:" +  cAgent.getIpAddress()
					+ ", tchannelIndex: " + tchannelIndex + " pAgent: " + pAgent.getIpAddress()
					+ "");
			return null;
		}
		
		String pApMacAddr = tApNode.apMAC;
		int crssi = staChaHandList[channelIndex].findAvgRssiByApMac(cAgentMacAddr);
		int cmark = 2 * crssi - 3 * cAgent.getStaCountOnline();
		int prssi = staChaHandList[tchannelIndex].findAvgRssiByApMac(tAgentMacAddr);
		int pmark = 2 * prssi - 3 * this.getAgent(tAgentMacAddr).getStaCountOnline();
		
		// 同信道+15；同频段+30;'
		boolean sameFreq = false;// 同频标识
		if ((channel <= 13 && tchannel <= 13) || (channel >= 14 && tchannel >= 14))// 表示同频段,2.4g或都是5g
		{
			sameFreq = true;// 同频
		}

		if (tchannel != channel) {// 不同信道
			pmark -= 15;
		}

		if (!sameFreq) {// 不同频段
			pmark -= 50;// 漫游同频优先 高50分左右
		}

		//Date nowTime = new Date();
		//SimpleDateFormat time = new SimpleDateFormat("yyyy-MM-dd HH mm ss");
		Master.consolePrintf("pmark: " + pmark + ", cmark: " + cmark);
		Master.consolePrintf("pmark-cmark : "+ pmark + " - "+cmark+"="+(pmark-cmark) 
					+ ", cAP:" + cAgent.getIpAddress() + "/"+ cAgent.getMode()
					+ ", pAP:" + this.getAgent(tAgentMacAddr).getIpAddress() + "/" + this.getAgent(tAgentMacAddr).getMode() 
					+ ", station: " + staMacAddress);
		if (pmark >= cmark + this.threshold1) {
			// pApMAC = tap.apMAC;
			this.noForceRoamMark = true;
			Master.consolePrintf("noForceRoamMark set true,pmark-cmark >=" + this.threshold1+" handoff, sta:" + staMacAddress);
			if (pmark > cmark + this.threshold2) {
				this.forceRoamMark = true;
				Master.consolePrintf("forceRoamMark set true, pmark-cmark >=" + this.threshold2 + " handoff, sta:" + staMacAddress);
			}
		}

		// 强制切换1：12s没有收到原agent的probe
		if (tApNode.old_date - cApNode.new_date > 12 * 1000) {
			pApMacAddr = tApNode.apMAC;
			this.noForceRoamMark = true;			
			Master.consolePrintf("12s passed after last probe from:" + cAgent.getIpAddress()+"/"+cAgent.getMacAddress()+"/"+cAgent.getMode()
				+ ", noForceRoamMark set true.");
		}

		//pAgent如果已经下发过对应的vap，则返回null
		Iterator<Entry<String, VapContainer>> it = station.getVapContainerSet().entrySet().iterator();
		VapContainer vapContainer = null;
		while (it.hasNext()) {
			vapContainer = it.next().getValue();
			if (vapContainer.getStation() == station 
				&& vapContainer.getSsid().equals(workingSsid)
				&&(vapContainer.getAgent() == pAgent) ){				 
				Master.consolePrintf("Vap has added for " + pAgent.getIpAddress() + "/" + pAgent.getMode()
						+ "deCurrentApMark set true," + "and, forceRoamMark is " +this.forceRoamMark
						+ ",if both true,  will del vap from cAgent:" 
						+ cAgent.getIpAddress()+"/"+ cAgent.getMacAddress()+ "/" + cAgent.getMode());
				this.deCurrentApMark = true;
				return null;				
			}
		}
		
		if (pApMacAddr != null && pApMacAddr != cAgentMacAddr) {			
			if (pAgent.getStaCountIdle() > 0) {
				return pAgent;
			}
			this.deCurrentApMark = false;//pagent没有空闲,cAgent不能删
		} 
		return null;
	}	
}

class chaHandList {
	protected HashMap<String, apHandNode> list;
	protected int queuesize;
	protected Date date;

	public chaHandList(int queueSize) {
		this.list = new HashMap<String, apHandNode>();
		this.queuesize = queueSize;
	}
	// 新AP来的Probe，如果不在就加进去
	public void addAP(String MAC, int RSSI, int channel) {
		this.date = new Date();
		apHandNode ap;

		if (this.list.containsKey(MAC)) {
			ap = this.list.get(MAC);
			if(channel > 13){
				this.fixRssiQueue(ap.RSSI, RSSI+50);
			}
			else{
				this.fixRssiQueue(ap.RSSI, RSSI);
			}
			ap.old_date = ap.new_date;
			ap.new_date = System.currentTimeMillis();
			return;
		}

		ap = new apHandNode(MAC, RSSI);
		for (int i = 0; i < queuesize; i++) {
			this.fixRssiQueue(ap.RSSI, -50);
		}
		
		if(channel > 13){
			this.fixRssiQueue(ap.RSSI, RSSI+50);
		}
		else{
			this.fixRssiQueue(ap.RSSI, RSSI);
		}
		
		this.list.put(MAC, ap);
	}

	// 固定队列
	public void fixRssiQueue(Queue<Integer> linklist, int rssi)// uint8->int8
	{
		if (linklist.size() < this.queuesize) {
			linklist.offer(rssi);
		} else {
			linklist.poll();
			linklist.offer(rssi);
		}
	}

	// 找到一个队列
	public Queue<Integer> findRssiByStaMac(String MAC) {
		apHandNode ap;
		/*
		 * 16 Iterator<apHandNode> it = list.iterator(); while (it.hasNext()) {
		 * ap = it.next(); if (ap.apMAC.equals(MAC)) { return ap.RSSI; } }
		 */
		Iterator<Map.Entry<String, apHandNode>> apitr = list.entrySet()
				.iterator();
		while (apitr.hasNext()) {
			Map.Entry<String, apHandNode> entry = apitr.next();
			ap = entry.getValue();
			return ap.RSSI;
		}
		return null;
	}

	// 找到一个队列平均值
	public int findAvgRssiByApMac(String MAC) {
		apHandNode ap;
		int[] a = new int[this.queuesize];
		ap = this.list.get(MAC);
		if (ap != null) {
			int i = 0;
			for (Integer x : ap.RSSI) {				
				a[i] = x;
				i++;
			}
			Arrays.sort(a);
			return a[(this.queuesize + 1) / 2 - 1];
		}
		return 0;
	}


	public apHandNode getApNodeByApMac(String MAC) {
		apHandNode ap = null;
		/*
		 * Iterator<apHandNode> it = list.iterator(); while (it.hasNext()) { ap
		 * = it.next(); if (ap.apMAC.equals(MAC)) { return ap; } }
		 */
		ap = this.list.get(MAC);
		return ap;
	}
}

class apHandNode {
	public String apMAC;// AP MAC
	public long old_date;
	public long new_date;
	public Queue<Integer> RSSI;

	public apHandNode() {
	}

	public apHandNode(String MAC, int RSSI) {
		this.apMAC = MAC;
		this.RSSI = new LinkedList<Integer>();
		this.old_date = System.currentTimeMillis();
		this.new_date = System.currentTimeMillis();
	}
}