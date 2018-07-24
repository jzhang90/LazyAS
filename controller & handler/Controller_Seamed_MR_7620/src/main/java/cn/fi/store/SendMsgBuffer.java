
package cn.fi.store;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import cn.fi.obj.SendMsg;

/**
 * 命令消息发送缓冲，存储控制器下发的命令消息，便于收到AP的回复后进行相应的视图更改等操作
 * 
 */
public class SendMsgBuffer {
	// 命令的三种状态，分别为执行失败，执行成功，未回复
	public static final String MSG_ERR = "err";
	public static final String MSG_OK = "ok";
	public static final String MSG_UNREPLY = "unreply";
	// 存储map，以消息sendMsgId为索引
	private ConcurrentHashMap<String, SendMsg> msgMap = new ConcurrentHashMap<String, SendMsg>();

	// 存储命令消息
	public synchronized void putSendMsg(String sendMsgId, SendMsg sendMsg) {
		msgMap.put(sendMsgId, sendMsg);
	}

	// 删除命令消息
	public synchronized void removeSendMsg(String sendMsgId) {
		msgMap.remove(sendMsgId);
	}

	// get set
	public SendMsg getSendMsg(String sendMsgId) {
		return msgMap.get(sendMsgId);
	}

	public Map<String, SendMsg> getSendMsgBuffer() {
		return Collections.unmodifiableMap(msgMap);
	}
}
