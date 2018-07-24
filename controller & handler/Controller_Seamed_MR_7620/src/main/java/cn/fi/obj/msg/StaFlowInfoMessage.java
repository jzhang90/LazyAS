package cn.fi.obj.msg;

import cn.fi.obj.Agent;

/*
 * AP发送给控制器的终端station流量消息
 * 
 * */
public class StaFlowInfoMessage extends Message {
	private String StaMacAddr;
	private long upTraffic;
	private long downTraffic;

	public StaFlowInfoMessage(String msgId, String msgType, int msgLen, String StaMacAddr, long upTraffic,
			long downTraffic, Agent agent) {
		super(msgId, msgType, msgLen, agent);
		this.StaMacAddr = StaMacAddr;
		this.upTraffic = upTraffic;
		this.downTraffic = downTraffic;
	}

	/* get set方法 */
	public String getStaMacAddr() {
		return StaMacAddr;
	}

	public void setStaMacAddr(String StaMacAddr) {
		this.StaMacAddr = StaMacAddr;
	}

	public long getUpTraffic() {
		return upTraffic;
	}

	public void setUpTraffic(long upTraffic) {
		this.upTraffic = upTraffic;
	}

	public long getDownTraffic() {
		return downTraffic;
	}

	public void setDownTraffic(long downTraffic) {
		this.downTraffic = downTraffic;
	}

	@Override
	public String toString() {
		return super.toString() + "StaFlowInfoMessage [StaMacAddr=" + StaMacAddr + ", upTraffic=" + upTraffic
				+ ", downTraffic=" + downTraffic + "]";
	}

}
