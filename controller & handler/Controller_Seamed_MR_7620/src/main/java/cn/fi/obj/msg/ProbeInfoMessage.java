package cn.fi.obj.msg;

import cn.fi.obj.Agent;

/*
 * AP发送给控制器的其收到的终端的Probe/数据帧消息
 * 
 * */
public class ProbeInfoMessage extends Message {
	private String StaMacAddr;
	private int rssi;
	private int au;
	private int channel;
	private String mode;

	public ProbeInfoMessage(String msgId, String msgType, int msgLen, String StaMacAddr, int rssi,int au, int channel,
			String mode, Agent agent) {
		super(msgId, msgType, msgLen, agent);
		this.StaMacAddr = StaMacAddr;
		this.rssi = rssi;
		this.au = au;
		this.channel = channel;
		this.mode = mode;
	}

	/* get set方法 */
	public String getStaMacAddr() {
		return StaMacAddr;
	}

	public void setStaMacAddr(String StaMacAddr) {
		this.StaMacAddr = StaMacAddr;
	}

	public int getRssi() {
		return rssi;
	}

	/** 
	 * @return au 
	 */
	public int getAu() {
		return au;
	}

	/**
	 * @param au the au to set
	 */
	public void setAu(int au) {
		this.au = au;
	}

	public void setRssi(int rssi) {
		this.rssi = rssi;
	}

	public int getChannel() {
		return channel;
	}

	public void setChannel(int channel) {
		this.channel = channel;
	}

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	@Override
	public String toString() {
		return super.toString() + "ProbeInfoMessage [StaMacAddr=" + StaMacAddr + ", rssi=" + rssi + ", channel="
				+ channel + ", mode=" + mode + "]";
	}

}