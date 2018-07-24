package cn.fi.obj.msg;

import cn.fi.obj.Agent;

/*
 * 控制器发送给AP的用于为终端分配Vap的addVap消息和删除分配Vap的delVap消息
 * 
 * */
public class StationCtrlMessage extends Message {
	private String staMacAddr;
	private String ssid;
	private String mode;

	public StationCtrlMessage(String msgId, String msgType, int msgLen, String staMacAddr, 
			String ssid, String mode, Agent agent) {
		super(msgId, msgType, msgLen, agent);
		// TODO Auto-generated constructor stub
		this.staMacAddr = staMacAddr;
		this.ssid = ssid;
		this.mode = mode;
	}

	/* get set方法 */
	public String getStaMacAddr() {
		return staMacAddr;
	}

	public void setStaMacAddr(String staMacAddr) {
		this.staMacAddr = staMacAddr;
	}

	public String getSsid() {
		return ssid;
	}

	public void setSsid(String ssid) {
		this.ssid = ssid;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	@Override
	public String toString() {
		return super.toString() + "StationCtrlMessage [staMacAddr=" + staMacAddr +// ", vapMacAddr=" + vapMacAddr + 
				", ssid="+ ssid + ", mode=" + mode + "]";
	}

}