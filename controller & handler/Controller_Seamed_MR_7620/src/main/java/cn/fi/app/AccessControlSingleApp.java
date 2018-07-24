package cn.fi.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.Iterator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cn.fi.main.Master;
import cn.fi.obj.Agent;
import cn.fi.obj.Application;
//import cn.fi.obj.Vap;
import cn.fi.obj.msg.Message;
import cn.fi.obj.msg.ProbeInfoMessage;
import cn.fi.obj.msg.StaCtrlMessage;
import cn.fi.obj.sub.EventSubscription;
import cn.fi.obj.sub.NotificationCallback;
import cn.fi.obj.sub.NotificationCallbackContext;
//0-13(2.4G)
//14- (5G)

public class AccessControlSingleApp extends Application {
	private String appName = "AccessControl";
	private Log log = LogFactory.getLog(this.getClass());
	private int waitTimeout = 1200;// 1200s
	private Date timeout = null;
	private int minint = Integer.MIN_VALUE;
	private int allChannel = 26;

	// 存储每个station对应的信道,所有AP的扫描情况,分配的信道,AP和时间
	private Hashtable<String, chaList[]> sta2ChannelScan = new Hashtable<String, chaList[]>();
	private String[] macAddrList = new String[100];
	private String macAddrFile = "macAddrList";

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
				handle(es, cntx);
			}
		};
		NotificationCallback cb1 = new NotificationCallback() {// 收到订阅的消息时 的回调函数
			@Override
			public void exec(EventSubscription es1, NotificationCallbackContext cntx) {
				handle1(es1, cntx);
			}
		};

		NotificationCallback cb2 = new NotificationCallback() {// 收到订阅的消息时 的回调函数
			@Override
			public void exec(EventSubscription es2, NotificationCallbackContext cntx) {
				handle1(es2, cntx);
			}
		};

		int length = readList(macAddrFile, macAddrList);
		/* 如果要订阅所有的，再地址列表第一行置为* */
		for (int i = 0; i < length; i++) {
			EventSubscription es = new EventSubscription(macAddrList[i], Message.FNII_MSG_PROBE_INFO, Master.OFF_LINE);
			registerSubscription(appName, es, cb);
			// new station message
			EventSubscription es1 = new EventSubscription(macAddrList[i], Message.FNII_MSG_NEW_STA, Master.ON_LINE);
			registerSubscription(appName, es1, cb1);

			EventSubscription es2 = new EventSubscription(macAddrList[i], Message.FNII_MSG_PROBE_INFO, Master.ON_LINE);
			registerSubscription(appName, es2, cb2);

			if (macAddrList[i].equals(Master.ALL)) {
				break;
			}
		}
	}

	private synchronized void handle1(EventSubscription oes, NotificationCallbackContext cntx) {
		StaCtrlMessage msg = (StaCtrlMessage) cntx.message;
		String staMACAddress = msg.getStaMacAddr();
		this.sta2ChannelScan.remove(staMACAddress);
	}

	/**
	 * @param oes
	 * @param cntx
	 */
	private synchronized void handle(EventSubscription oes, NotificationCallbackContext cntx) {
		// long startTime = System.nanoTime();
		// 初始化存储每个信道当前sta的数量为0
		ProbeInfoMessage msg = (ProbeInfoMessage) cntx.message;
		Agent agent = msg.getAgent();
		String staMacAddress = msg.getStaMacAddr();
		int channel = msg.getChannel();
		/* 第一个上报probe的AP */
		// log.info("第一轮扫描");
		if (this.sta2ChannelScan.get(staMacAddress) == null) {
			chaList[] tCha = new chaList[allChannel + 1];
			this.sta2ChannelScan.put(staMacAddress, tCha);
		}
		// 初始化
		if (this.sta2ChannelScan.get(staMacAddress)[chaList.channeltoIndex(channel)] == null) {
			this.sta2ChannelScan.get(staMacAddress)[chaList.channeltoIndex(channel)] = new chaList();
		}
		this.sta2ChannelScan.get(staMacAddress)[chaList.channeltoIndex(channel)].addAP(agent.getMacAddress(),
				msg.getRssi(), channel);
		ArrayList<Agent> agents = this.channelAPSelecM2(staMacAddress);
		if (agents == null) {
			log.info("分配失败");
		} else {
			Iterator<Agent> it = agents.iterator();
			while (it.hasNext()) {
				Agent tagent = it.next();
				Agent objAgent = tagent;

				this.addVapContainer(false, objAgent, staMacAddress, msg.getMode(),
						agent.getSsidSet().iterator().next());
				System.out.println("S-AddVAP--STAMAC:" + msg.getStaMacAddr() + "AP:" + objAgent.getIpAddress());
			}
		}

		// 清除working的sta,全局周期时间执行 120s
		Date time = new Date();
		if (timeout == null || (timeout != null && time.getTime() - timeout.getTime() > this.waitTimeout * 1000)) {
			Iterator<String> stamacitr = this.sta2ChannelScan.keySet().iterator();
			while (stamacitr.hasNext()) {
				String stamac = stamacitr.next();
				if (this.getStation(stamac) != null) {
					long lasttime = 0;
					for (int i = 1; i < allChannel; i++) {
						if (this.sta2ChannelScan.get(stamac)[i] != null) {
							long t = this.sta2ChannelScan.get(stamac)[i].date;
							if (lasttime < t || lasttime == 0) {
								lasttime = t;
							}
							if (time.getTime() - t > this.waitTimeout * 1000) {
								this.sta2ChannelScan.get(stamac)[i] = null;
							}
						}
					}
					// 所有信道中来的最晚的time
					if (time.getTime() - lasttime > this.waitTimeout * 1000) {
						stamacitr.remove();
					}
				}
			}
			this.timeout = new Date();
		}
	}

	// 判断是不是第二轮扫描
	public boolean scan2nd(String staMAC) {
		if (!this.sta2ChannelScan.containsKey(staMAC)) {
			return false;
		} else {
			chaList tChaList;
			apNode ap;
			for (int i = 1; i < allChannel; i++) {
				tChaList = this.sta2ChannelScan.get(staMAC)[i];
				if (tChaList == null) {
					continue;
				}
				Iterator<apNode> it = tChaList.list.iterator();
				while (it.hasNext()) {
					ap = it.next();
					if (ap.scan > 1) {
						return true;
					}
				}
			}
			return false;
		}
	}

	// 选择信道和AP的总函数
	public ArrayList<Agent> channelAPSelecM2(String staMAC) {
		ArrayList<Agent> agentAgent = null;
		chaList cha[] = this.sta2ChannelScan.get(staMAC);
		apNode ap;
		int mark = minint;
		int prssi;
		int pmark;
		for (int i = 1; i < allChannel; i++) {
			if (cha[i] == null) {
				continue;
			}
			Iterator<apNode> it = cha[i].list.iterator();
			// 挂掉的AP和没有VAP的AP去掉
			while (it.hasNext()) {
				ap = it.next();
				// 如果AP挂了
				if (this.getAgent(ap.apMAC) == null) {
					cha[i].list.remove(ap.apMAC);
					continue;
				}

				int count = 0;
				count = this.getAgent(ap.apMAC).getStaCountIdle();

				if (count == 0) {
					continue;
				}
				prssi = cha[i].findRSSIByAPMAC(ap.apMAC);
				int num = this.getAgent(ap.apMAC).getStaCountOnline() + this.getAgent(ap.apMAC).getStaCountOnline();
				pmark = 2 * prssi - 3 * num;
				if (mark == minint || pmark > mark) {
					mark = pmark;
					if (agentAgent != null) {
						agentAgent.clear();
					} else {
						agentAgent = new ArrayList<Agent>();
					}
					agentAgent.add(this.getAgent(ap.apMAC));
				}
			}
		}
		return agentAgent;
	}
}

class chaList {
	protected ArrayList<apNode> list;
	private final int PRIORITY_5G = 500;
	public long date;
	public chaList() {
		this.list = new ArrayList<apNode>();
		this.date = System.currentTimeMillis();
	}
	
	//36(14)-48//149(18)-165
	public static int channeltoIndex(int channel){
		int index=0;
    	if(channel < 14)
    	{
    		index=channel;
    	}else if(channel<65)//36(14),40, 44, 48, 52, 56, 60, 64(21),
    	{
    		index = 5 + channel/4;
    	}
    	// 149->22;165->22;
    	else if(channel < 166)//149(22),153, 157, 161, 165(26)
    	{
    		index = -15 + channel/4;
    	}
    	return index;
	}
	
	// 新AP来的Probe，如果不在就加进去
	public void addAP(String MAC, int RSSI, int channel) {
		apNode ap;
		Iterator<apNode> it = list.iterator();
		while (it.hasNext()) {
			ap = it.next();
			if (ap.apMAC.equals(MAC)) {
				if (System.currentTimeMillis() - ap.date<3000 &&RSSI > ap.RSSI) {
					//ap.RSSI = RSSI;
					if(chaList.channeltoIndex(channel)<14)
					{
						ap.RSSI = RSSI;
					}else{
						ap.RSSI = RSSI + PRIORITY_5G;
					}
				}else if(System.currentTimeMillis() - ap.date>3000)
				{
					ap.date=System.currentTimeMillis();
					ap.scan = 1;
					if(chaList.channeltoIndex(channel) < 14)
					{
						ap.RSSI = RSSI;
					}else{
						ap.RSSI = RSSI+ PRIORITY_5G;
					}
				}
				ap.scan += 1;
				ap.date = System.currentTimeMillis();
				System.out.println("---new added apNode : "+ ap.apMAC + " date:" + ap.date);
				return;
			}
		}
		if(chaList.channeltoIndex(channel)<14)
		{
			ap = new apNode(MAC, RSSI, System.currentTimeMillis());
		}else{

			ap = new apNode(MAC, RSSI+50, System.currentTimeMillis());
		}
		
		this.list.add(ap);
		return;
	}

	//返回某个STA在某个AP上的RSSI
	public int findRSSIByAPMAC(String MAC) {
		apNode ap;
		Iterator<apNode> it = list.iterator();
		while (it.hasNext()) {
			ap = it.next();
			if(ap.apMAC.equals(MAC))
			{
				return ap.RSSI;
			}
		}
		return 0;
	}
}

class apNode {
	public String apMAC;// AP MAC
	public int scan;
	public int RSSI;
	public long date;

	public apNode() {
	}

	public apNode(String MAC, int RSSI, long time) {
		this.apMAC = MAC;
		this.RSSI = RSSI;
		this.scan = 1;
		this.date = time;
	}
}