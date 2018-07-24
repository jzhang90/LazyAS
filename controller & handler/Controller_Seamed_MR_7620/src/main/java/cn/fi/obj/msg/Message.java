package cn.fi.obj.msg;

import cn.fi.obj.Agent;

/*
 * 消息基类
 * 
 * */
public class Message {
	/* mode的取值 */
	public final static String NET_2_4G = "010";// 2.4ghz
	public final static String NET_5G = "011";// 5ghz
	/* msgType的取值，即消息的类型 */
	public static final String FNII_MSG_OK = "100";
	public static final String FNII_MSG_ERR = "101";
	
	//控制器下发命令类型	
	public static final String FNII_MSG_ADD_VAP_ROAM = "202";//控制器使用，ap不识别。	
	public static final String FNII_MSG_ADD_VAP = "102";
	public static final String FNII_MSG_DEL_VAP = "103";
	public static final String FNII_MSG_OFFLINE_VAP= "117";//强制漫游时，delVap修改为OFFLINE_VAP,ap不删除此vap
	
	//agent上报消息
	public static final String FNII_MSG_NEW_STA = "104";
	public static final String FNII_MSG_DEL_STA = "105";
	public static final String FNII_MSG_FLOW_INFO = "106";
	public static final String FNII_MSG_PROBE_INFO = "107";
	public static final String FNII_MSG_IF_INFO = "108";
	public static final String FNII_MSG_HEART_BEAT = "110";
	public static final String FNII_MSG_INTERFACE_NUM = "111";//上报ap的属性信息
	public static final String FNII_MSG_LOCAL_MAC = "109";
	public static final String FNII_MSG_UPDATE_STA = "116";
	
	public static final String FNII_ROAM_TYPE_FORCE = "01";
	public static final String FNII_ROAM_TYPE_DELCAGENT = "02";
	
	private String msgId;// 消息ID
	private String msgType;// 消息类型
	private int msgLen;// 消息的总长度
	private Agent agent;// 汇报消息的agent或发送消息的目标agent

	public Message(String msgId, String msgType, int msgLen, Agent agent) {
		super();
		this.msgType = msgType;
		this.msgLen = msgLen;
		this.agent = agent;
	}

	/* get、set方法 */
	public String getMsgId() {
		return msgId;
	}

	public void setMsgId(String msgId) {
		this.msgId = msgId;
	}

	public Agent getAgent() {
		return agent;
	}

	public void setAgent(Agent agent) {
		this.agent = agent;
	}

	public String getMsgType() {
		return msgType;
	}

	public void setMsgType(String msgType) {
		this.msgType = msgType;
	}

	public int getMsgLen() {
		return msgLen;
	}

	public void setMsgLen(int msgLen) {
		this.msgLen = msgLen;
	}

	@Override
	public String toString() {
		return "Message [msgType=" + msgType + ", msgLen=" + msgLen + ", agent=" + agent + "]";
	}
}