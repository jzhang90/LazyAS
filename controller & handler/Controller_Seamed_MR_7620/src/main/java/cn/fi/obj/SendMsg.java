package cn.fi.obj;

import cn.fi.obj.msg.Message;

/*命令消息发送缓冲中的消息对象*/
public class SendMsg {
	private Message msg; // 对象的实际消息
	private String status;// 命令执行结果，分为err，ok，unreply

	public SendMsg(Message msg, String status) {
		super();
		this.msg = msg;
		this.status = status;
	}

	/* get set方法 */
	public Message getMsg() {
		return msg;
	}

	public void setMsg(Message msg) {
		this.msg = msg;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	@Override
	public String toString() {
		return "SendMsg [msg=" + msg + ", status=" + status + "]";
	}

}