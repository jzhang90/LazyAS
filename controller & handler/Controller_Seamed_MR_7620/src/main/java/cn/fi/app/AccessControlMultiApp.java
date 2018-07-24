 package cn.fi.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Date;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cn.fi.main.Master;
import cn.fi.obj.Agent;
import cn.fi.obj.Application;
import cn.fi.obj.msg.Message;
import cn.fi.obj.msg.ProbeInfoMessage;
import cn.fi.obj.msg.StaCtrlMessage;
import cn.fi.obj.sub.EventSubscription;
import cn.fi.obj.sub.NotificationCallback;
import cn.fi.obj.sub.NotificationCallbackContext;

public class AccessControlMultiApp extends Application {
	private String appName = "AccessControl";
	private Log log = LogFactory.getLog(this.getClass());
	private int waitTimeout = 120;//
	private Date timeout = null;
	private int allChannel = 26;
	// 存储每个station对应的信道,所有AP的扫描情况,分配的信道,AP和时间
	private Hashtable<String, chaList[]> sta2ChannelScan = new Hashtable<String, chaList[]>();
	private Hashtable<String, Date> sta2DisTime = new Hashtable<String, Date>();// staMAC-Date
	private String[] macAddrList = new String[100];
	private String macAddrFile = "macAddrList";
	HashSet<apNode> faultAP = new HashSet<apNode>();//如果ssid不属于此ap，将此ap保存在faultAP中
	Set<String> ssidOnAP = new HashSet<String>();
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
				handle(es2, cntx);
			}
		};
		/*
		 * NotificationCallback cb3 = new NotificationCallback() {// 收到订阅的消息时
		 * 的回调函数
		 * 
		 * @Override public void exec(EventSubscription es3,
		 * NotificationCallbackContext cntx) { handle(es3, cntx); } };
		 */

		int length = readList(macAddrFile, macAddrList);
		/* 如果要订阅所有的，再地址列表第一行置为* */
		for (int i = 0; i < length; i++) {
			EventSubscription es = new EventSubscription(macAddrList[i], Message.FNII_MSG_PROBE_INFO, Master.OFF_LINE);
			registerSubscription(appName, es, cb);
			// new station message
			EventSubscription es1 = new EventSubscription(macAddrList[i], Message.FNII_MSG_NEW_STA, Master.ON_LINE);
			registerSubscription(appName, es1, cb1);
			// 上线后的为了多SSID
			EventSubscription es2 = new EventSubscription(macAddrList[i], Message.FNII_MSG_PROBE_INFO, Master.ON_LINE);
			registerSubscription(appName, es2, cb2);

			// EventSubscription es3 = new EventSubscription(macAddrList[i],
			// Message.FNII_MSG_PROBE_INFO, Master.IDLE);
			// registerSubscription(appName, es3, cb3);

			if (macAddrList[i].equals(Master.ALL)) {
				break;
			}
		}
	}

	private synchronized void handle1(EventSubscription oes, NotificationCallbackContext cntx) {
		StaCtrlMessage msg = (StaCtrlMessage) cntx.message;
		String staMACAddress = msg.getStaMacAddr();
		this.sta2ChannelScan.remove(staMACAddress);
		this.sta2DisTime.remove(staMACAddress);
	}

	
	public void delStation(String staMacAddress){
		Date time = new Date();
		if (timeout == null || (timeout != null && time.getTime() - timeout.getTime() > this.waitTimeout * 1000))// 120s
		{
			Iterator<String> itStationMac = this.sta2ChannelScan.keySet().iterator();
			while (itStationMac.hasNext()) {
				String staMac = itStationMac.next();
				if (this.getStation(staMac) != null) {
					long endTime = 0;
					// 超时120s的AP都删除
					for (int i = 1; i < allChannel; i++) {
						if (this.sta2ChannelScan.get(staMac)[i] != null) {
							long t = this.sta2ChannelScan.get(staMac)[i].date;
							if (endTime < t || endTime == 0) {
								endTime = t;
							}
							if (time.getTime() - t > this.waitTimeout * 1000) {
								this.sta2ChannelScan.get(staMac)[i] = null;
							}
						}
					}
					if (time.getTime() - endTime > this.waitTimeout * 1000) {
						this.sta2ChannelScan.remove(staMacAddress);
						itStationMac.remove();
					}
				}
			}
			this.timeout = new Date();
		}
	}	
	/**
	 * @param oes
	 * @param cntx
	 */
	private synchronized void handle(EventSubscription oes, NotificationCallbackContext cntx) {
		ProbeInfoMessage msg = (ProbeInfoMessage) cntx.message;
		Agent agent = msg.getAgent();
		String staMacAddress = msg.getStaMacAddr();		
		int channel = msg.getChannel();

		//System.out.println("...handle: " + agent.getSsidSet());
		//System.out.println("... hansle:" + this.getAgent(agent.getMacAddress()).getSsidSet());
		// Master.consolePrintf("MSG:"+msg.getAgent().getIpAddress()+ " " +
		// " AgentMac: "+ msg.getAgent().getMacAddress()+
		// channel+ " staMac:"+ staMacAddress + " mode: "+ msg.getMode());
		/* 第一轮扫描，第一个上报probe的AP */
		// log.info("第一轮扫描");
		if (this.sta2ChannelScan.get(msg.getStaMacAddr()) == null) {
			chaList[] tCha = new chaList[allChannel + 1];
			this.sta2ChannelScan.put(msg.getStaMacAddr(), tCha);
		}
		// chu shi hua
		int channelIndex = chaList.channeltoIndex(channel);
		if (this.sta2ChannelScan.get(msg.getStaMacAddr())[channelIndex] == null) {
			this.sta2ChannelScan.get(msg.getStaMacAddr())[channelIndex] = new chaList();
		}
		this.sta2ChannelScan.get(msg.getStaMacAddr())[channelIndex].addAP(agent.getMacAddress(), msg.getRssi(), channel);
		//System.out.println("addApNode : " + agent.getIpAddress()+ "/"+agent.getMacAddress()+"/"+agent.getMode());
		// 多ssid 版本
		Set<String> ssidAroundSet = this.ssidOnallAP(staMacAddress);
		//System.out.println("..............."+ agent.getIpAddress()+ "/"+agent.getMacAddress() + "/"+ agent.getMode() 
		//					+"ssidset: " + ssidAroundSet);
		Set<String> disSsidSet = new HashSet<String>();// 要分配的剩下的	
		
		Iterator<String> itSsidAround = ssidAroundSet.iterator();
		String ssidAround = null;
		while (itSsidAround.hasNext()) {
			ssidAround = itSsidAround.next();
			disSsidSet.add(ssidAround);
			//System.out.println("..............."+ agent.getIpAddress()+ "/"+agent.getMacAddress() + "/"+ agent.getMode() 
			//				+" add : " + ssidAround);
		}
		if (disSsidSet.size() == 0) {
			log.info("!! disSsidSet.size() == 0");
			return;
		}
		Hashtable<String, Agent> agentHash = null;
		agentHash = this.channelAPSelecM2(msg.getStaMacAddr(), disSsidSet);
		
		if (agentHash == null) {
			log.info("分配失败");
		} else {
			//System.out.println("........agentHash keyset(ssidset) :" + agentHash.keySet());
			Iterator<String> it = agentHash.keySet().iterator();
			while (it.hasNext()) {
				String SSID = it.next();
				//System.out.println("....." + SSID);
				Agent objAgent = agentHash.get(SSID);				 

				this.addVapContainer(false, objAgent, staMacAddress, msg.getMode(), SSID);
				//Master.consolePrintf("M-AddVAP--" + " AP:" + objAgent.getIpAddress() + ",SSID:" + SSID + " agentMac: "
				//		+ objAgent.getMacAddress() + " STAMAC:" + msg.getStaMacAddr());
			}
			this.sta2ChannelScan.remove(staMacAddress);
		}
		
		// 清除发了Probe没有连接的sta,全局周期时间执行
		this.delStation(staMacAddress);
	}

	public Set<String> ssidOnallAP(String staMAC) {
		Set<String> ssidset = new HashSet<String>();
		chaList[] chalist = this.sta2ChannelScan.get(staMAC);
		if (chalist == null) {
			return ssidset;
		}
		for (int i = 1; i < allChannel; i++) {
			if (chalist[i] == null) {
				continue;
			}
			Iterator<apNode> itrapnode = chalist[i].list.iterator();
			while (itrapnode.hasNext()) {
				apNode tapnode = itrapnode.next();
				// Set<String> tssidset = new HashSet<String>();
				if (this.getAgent(tapnode.apMAC) == null) {
					chalist[i].list.remove(tapnode.apMAC);
					continue;
				}

				// 把所有ap/agent的ssid添加到ssidset中。不能重复添加。
				Iterator<String> itAgentSsidSet = this.getAgent(tapnode.apMAC).getSsidSet().iterator();
				while (itAgentSsidSet.hasNext()) {
					String ssid = itAgentSsidSet.next();
					if (!ssidset.contains(ssid)) {
						ssidset.add(ssid);
					}
				}
			}
		}
		return ssidset;
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

	
	public void checkFakeApfromCha(String staMAC, int i, Set<String> disSSID){
		apNode ap = null;
		chaList cha[] = this.sta2ChannelScan.get(staMAC);
		Iterator<apNode> it = cha[i].list.iterator();
		Set<String> ssidSet = new HashSet<String>();
		// 挂掉的AP和没有该SSID的VAP的AP去掉
		while (it.hasNext()) {
			ap = it.next();
			// 或者AP上没有包含这些SSID,放到临时的set里面
			boolean ssidOnThisAP = false;			
			// 如果AP挂了没有空闲VAP
			Agent agent = this.getAgent(ap.apMAC);			
			if ((agent == null) || (agent != null && agent.getStaCountIdle() <= 0)) {
				if(cha[i].list.contains(ap.apMAC)){
					cha[i].list.remove(ap.apMAC);
				}					
				continue;
			}
			
			ssidSet = this.getAgent(ap.apMAC).getSsidSet();
			// 该AP上拥有的SSID是否在需要分配的里面
			Iterator<String> itrSsidSet = ssidSet.iterator();
			while (itrSsidSet.hasNext()) {
				String tssid = itrSsidSet.next();
				// Master.consolePrintf("tssid: "+tssid);
				if (disSSID.contains(tssid)) {
					ssidOnThisAP = true;
					break;
				}
			}
			if (!ssidOnThisAP) {
				faultAP.add(ap);
			}
		}
	}	
	
	public boolean disAgentCheck(String apMac, Hashtable<String, Integer> dismark, int pmark){
		boolean mark = false;
		//System.out.println("...disAgentCheck "+this.getAgent(apMac).getSsidSet());
		Iterator<String> it = this.getAgent(apMac).getSsidSet().iterator();
		while (it.hasNext()) {
			String ssidonthisap = it.next();
			if (ssidOnAP.size() == 0 || !ssidOnAP.contains(ssidonthisap)) {
				ssidOnAP.add(ssidonthisap);
				//System.out.println("....ssidOnAP add: " + ssidonthisap);
			}
			if (dismark.get(ssidonthisap) == null || dismark.get(ssidonthisap) < pmark) {
				mark = true;
			}
		}
		return mark;
	}
	
	// 选择信道和AP的总函数
	public Hashtable<String, Agent> channelAPSelecM2(String staMAC, Set<String> disSSID) {
		int prssi;
		int pmark;
		Hashtable<String, Agent> agentHashtable = null;
		chaList cha[] = this.sta2ChannelScan.get(staMAC);
		apNode ap = null;

		Hashtable<String, Integer> dismark = new Hashtable<String, Integer>();
		for (int i = 1; i < allChannel; i++) {
			if (cha[i] == null || cha[i].list.size() == 0) {
				continue;
			}
			
			// 挂掉的AP和没有该SSID的VAP的AP去掉
			this.checkFakeApfromCha(staMAC, i, disSSID);
						
			// 找该信道一个平均信号强度最强的AP
			Iterator<apNode> it = cha[i].list.iterator();			
			while (it.hasNext()) {
				ap = it.next();
				if (this.getAgent(ap.apMAC) == null || faultAP.contains(ap)) {
					continue;
				}
				prssi = cha[i].findRSSIByAPMAC(ap.apMAC);

				// working+online的数目，32-free
				int num = this.getAgent(ap.apMAC).getStaCountSum() - this.getAgent(ap.apMAC).getStaCountIdle();
				pmark = 2 * prssi - 3 * num;				
				boolean disAgentMark = this.disAgentCheck(ap.apMAC, dismark, pmark);
				if (disAgentMark) {
					// 存下AP和SSID
					if (agentHashtable == null) {
						agentHashtable = new Hashtable<String, Agent>();
					}

					int restVapIdle = 0; // 剩余的可分配VAP
					restVapIdle = this.getAgent(ap.apMAC).getStaCountIdle();

					int changeNum;// 可分配的个数
					changeNum = restVapIdle > ssidOnAP.size() ? ssidOnAP.size() : restVapIdle;
					//System.out.println("...ssidOnAP: " + ssidOnAP);
					Iterator<String> tssid = ssidOnAP.iterator();					 
					while (tssid.hasNext()) {
						if(changeNum > 0){
							String ssid = tssid.next();
							//System.out.println("...tssid: " + ssid);
							if (disSSID.contains(ssid) && 
									(dismark.get(ssid) == null ||pmark > dismark.get(ssid))) {
								dismark.put(ssid, pmark);
								if (this.getAgent(ap.apMAC).getSsidSet().contains(ssid)) {
									agentHashtable.put(ssid, this.getAgent(ap.apMAC));
								}
							}
							changeNum--;
						}else{
							break;
						}
					}					
				}
			}
		}
		return agentHashtable;
	}
}