package cn.fi.obj.msg;

import cn.fi.obj.Agent;

/*
 * AP发送给控制器的终端station上线/下线消息
 * 
 * */
public class StaCtrlMessage extends Message {
	private String StaMacAddr;
	private String vapMacAddr;

	public StaCtrlMessage(String msgId, String msgType, int msgLen, String StaMacAddr, Agent agent) {
		super(msgId, msgType, msgLen, agent);
		this.StaMacAddr = StaMacAddr;
		//this.vapMacAddr = vapMacAddr;
		// TODO Auto-generated constructor stub
	}

	/* get set方法 */
	public String getStaMacAddr() {
		return StaMacAddr;
	}

	public void setStaMacAddr(String StaMacAddr) {
		this.StaMacAddr = StaMacAddr;
	}

	public String getVapMacAddr() {
		return vapMacAddr;
	}

	public void setVapMacAddr(String vapMacAddr) {
		this.vapMacAddr = vapMacAddr;
	}

	@Override
	public String toString() {
		return super.toString() + "StaCtrlMessage [StaMacAddr=" + StaMacAddr + "]";
	}

}