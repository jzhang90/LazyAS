package cn.fi.obj.msg;

import cn.fi.obj.Agent;

/*
 * 控制器发送给AP的漫游切换命令消息
 * 
 * */
public class HandoffMessage extends StationCtrlMessage {
	String fromAgentMacAddr;

	public HandoffMessage(String msgId, String msgType, int msgLen, String staMacAddr, 
			String fromAgentMacAddr, String ssid, String mode, Agent agent) {
		super(msgId, msgType, msgLen, staMacAddr, ssid, mode, agent);
		//super(msgId, msgType, msgLen, staMacAddr, vapMacAddr, ssid, agent);
		this.fromAgentMacAddr = fromAgentMacAddr;
		// TODO Auto-generated constructor stub
	}

	public String getFromAgentMacAddr() {
		return fromAgentMacAddr;
	}

}