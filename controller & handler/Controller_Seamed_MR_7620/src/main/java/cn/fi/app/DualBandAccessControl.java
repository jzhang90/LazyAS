/**   
 * @Title: DualBandAccessControl.java 
 * @Package cn.fi.app 
 * @Description: TODO
 * @author layrong
 * @date 2016年12月6日 下午1:48:16 
 * @version V1.0   
 */
package cn.fi.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;

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

/**
 * @ClassName: DualBandAccessControl
 * @Description: TODO
 * @author layrong
 * @date 2016年12月6日 下午1:48:16
 * 
 */
class ProbeInfo {
	public int rssi;
	public int au;
	public Agent agent;

	public ProbeInfo() {
		rssi = -1;
		au = -1;
		agent = null;
	}

	/**
	 * @Title: ProbeInfo
	 * @Description: TODO
	 * @param @param rssi
	 * @param @param au
	 * @throws
	 */
	public ProbeInfo(int rssi, int au, Agent agent) {
		super();
		this.rssi = rssi;
		this.au = au;
		this.agent = agent;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @Title: toString
	 * 
	 * @Description:
	 * 
	 * @return
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ProbeInfo [rssi=" + rssi + ", au=" + au + "]";
	}

}

class TreeNode {
	TreeNode left;
	TreeNode right;
	TreeNode parent;
	String condition;
	double valueCon;
	String relation;
	boolean canPunching;
	int pos;
	int leaf; // -1 0 1 2

	TreeNode() {
		left = null;
		right = null;
		parent = null;
		leaf = -1;
		pos = -1;
		valueCon = 0;
		canPunching = false;
	}
}

class ProbeDeci {

	public int flag;
	public ProbeInfo probeInfos[];

	public ProbeDeci() {
		flag = -1;
		probeInfos = new ProbeInfo[2];
		probeInfos[0] = null;
		probeInfos[1] = null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @Title: toString
	 * 
	 * @Description:
	 * 
	 * @return
	 * 
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
		return "ProbeDeci [flag=" + flag + ", probeInfos="
				+ (probeInfos[0] != null ? probeInfos[0].toString() : "null")
				+ " "
				+ (probeInfos[1] != null ? probeInfos[1].toString() : "null")
				+ "]";
	}

}

public class DualBandAccessControl extends Application {
	/*
	 * (non-Javadoc)
	 * 
	 * @Title: run
	 * 
	 * @Description:
	 * 
	 * @see cn.fi.obj.Application#run()
	 */
	private Log log = LogFactory.getLog(this.getClass());
	private String appName = "DualBandAccessControl";
	private String[] macAddrList = new String[100];
	private String macAddrFile = "macAddrList";
	private HashMap<String, ProbeDeci> descMap = new HashMap<String, ProbeDeci>();
	private TreeNode decisionTree = null;
	private String treePath = "E:\\双频切换备份\\双频切换\\机器学习\\data8\\b\\c5.txt";
	TreeNode constructTree(String input) {
		HashMap<Integer, TreeNode> m = new HashMap<Integer, TreeNode>();
		TreeNode root = new TreeNode(), last = root;
		root.condition = "root";
		root.parent = null;
		BufferedReader br;
		try {
			br = new BufferedReader(new InputStreamReader(new FileInputStream(
					input)));
			String data = null;
			while ((data = br.readLine()) != null) {
				int pos1 = data.indexOf("r");
				if (pos1 == -1)
					pos1 = data.indexOf("a");
				int pos3 = data.indexOf(" ", pos1 + 1);
				int pos4 = data.indexOf(" ", pos3 + 1);
				int pos5 = data.indexOf(" ", pos4 + 1);
				TreeNode tmp = new TreeNode();
				tmp.relation = data.substring(pos3 + 1, pos4);
				tmp.condition = data.substring(pos1, pos3);
				tmp.pos = pos1;
				tmp.valueCon = Double.parseDouble(data
						.substring(pos4 + 1, pos5));
				if (last.pos >= pos1 && m.containsKey(pos1)) { // 重新找父节点
					last = m.get(pos1).parent;
				}
				m.put(pos1, tmp);
				if (last.left == null)
					last.left = tmp;
				else
					last.right = tmp;
				tmp.parent = last;
				int pos2 = data.indexOf("=>");
				if (pos2 != -1) {
					tmp.leaf = Integer.parseInt(data.substring(pos2 + 2,
							pos2 + 4).trim());
				}
				last = tmp;

			}
			br.close();
		} catch (NumberFormatException | IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return root;
	}

	int doDecision(ProbeDeci deci,
			TreeNode root) {
		TreeNode cur = root;
		while (cur.left != null) {
			int curValCon;
			boolean relation = false;
			if (cur.left.condition.equals("rssi_2.4")) {
				curValCon = deci.probeInfos[0].rssi;
			} else if (cur.left.condition.equals("rssi_5")) {
				curValCon = deci.probeInfos[1].rssi;
			} else if (cur.left.condition.equals("au_2.4")) {
				curValCon = deci.probeInfos[0].au;
			} else {
				curValCon = deci.probeInfos[1].au;
			}
			if (cur.left.relation.equals("<")) {
				relation = curValCon < cur.left.valueCon;
			} else if (cur.left.relation.equals("<=")) {
				relation = curValCon <= cur.left.valueCon;
			} else if (cur.left.relation.equals(">")) {
				relation = curValCon > cur.left.valueCon;
			} else if (cur.left.relation.equals(">=")) {
				relation = curValCon >= cur.left.valueCon;
			}
			if (relation) {
				cur = cur.left;
			} else {
				cur = cur.right;
			}
		}
		deci.flag = cur.leaf;
		return cur.leaf;
	}

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

	private void handle(EventSubscription es, NotificationCallbackContext cntx) {
		if (cntx.message.getMsgType().equals(Message.FNII_MSG_PROBE_INFO)) {
			ProbeInfoMessage msg = (ProbeInfoMessage) cntx.message;
			int mode = msg.getChannel() / 36;
			log.info("probe message" + msg.toString());
			if (descMap.containsKey(msg.getStaMacAddr())) {
				log.info("contains "
						+ descMap.get(msg.getStaMacAddr()).toString());
				if (descMap.get(msg.getStaMacAddr()).flag != -1) {
					// 已经决策过了
					return;
				}
				if (descMap.get(msg.getStaMacAddr()).probeInfos[mode] == null) {
					descMap.get(msg.getStaMacAddr()).probeInfos[mode] = new ProbeInfo(msg.getRssi(),
							msg.getAu(), msg.getAgent());
				}
				if (descMap.get(msg.getStaMacAddr()).probeInfos[1- mode] != null) {
					// 做决策
					doDecision(descMap.get(msg.getStaMacAddr()),decisionTree);
					log.info("desction is "
							+ descMap.get(msg.getStaMacAddr()).flag);
					if (descMap.get(msg.getStaMacAddr()).flag == 0) {
						this.addVapContainer(false, descMap.get(msg
								.getStaMacAddr()).probeInfos[0].agent, msg
								.getStaMacAddr(), Message.NET_2_4G, msg
								.getAgent().getSsidSet().iterator().next());
					} else {
						this.addVapContainer(false, descMap.get(msg
								.getStaMacAddr()).probeInfos[1].agent, msg
								.getStaMacAddr(), Message.NET_5G, msg
								.getAgent().getSsidSet().iterator().next());
					}
				}

			} else {
				log.info("not contains");
				descMap.put(msg.getStaMacAddr(), new ProbeDeci());
				descMap.get(msg.getStaMacAddr()).probeInfos[mode] = new ProbeInfo(
						msg.getRssi(), msg.getAu(), msg.getAgent());
			}
			// log.info("ends " + descMap.get(msg.getStaMacAddr()).toString());

		} else if (cntx.message.getMsgType().equals(Message.FNII_MSG_DEL_STA)) {
			StaCtrlMessage msg = (StaCtrlMessage) cntx.message;
			descMap.remove(msg.getStaMacAddr());
		}
	}

	 
	private void init() {
		log.info("init" + appName);
		NotificationCallback cb = new NotificationCallback() {// 收到订阅的消息时 的回调函数
			@Override
			public void exec(EventSubscription es,
					NotificationCallbackContext cntx) {
				handle(es, cntx);
			}
		};

		int length = readList(macAddrFile, macAddrList);
		/* 如果要订阅所有的，再地址列表第一行置为* */
		for (int i = 0; i < length; i++) {
			EventSubscription es = new EventSubscription(macAddrList[i],
					Message.FNII_MSG_PROBE_INFO, Master.OFF_LINE);
			registerSubscription(appName, es, cb);
			EventSubscription es1 = new EventSubscription(macAddrList[i],
					Message.FNII_MSG_DEL_STA, Master.ON_LINE);
			registerSubscription(appName, es1, cb);

			if (macAddrList[i].equals(Master.ALL)) {
				break;
			}
		}
		decisionTree = constructTree(treePath);
	}
}
