package cn.fi.obj;

import java.nio.channels.SelectionKey;

/**
 * 视图中的套接字信息对象，主要包含套接字对应的上次未解析的消息残留（由于消息不完整）
 * 
 */
public class SocketInfo {
	private String legacy;// 套接字对应的上次未解析的消息残留（由于消息不完整）
	private SelectionKey key;// 套接字对应的key

	public SocketInfo(SelectionKey key) {
		super();
		this.legacy = "";// 初始化为“”便于拼接
		this.key = key;
	}

	/* get set方法 */
	public String getLegacy() {
		return legacy;
	}

	public void setLegacy(String legacy) {
		this.legacy = legacy;
	}

	public SelectionKey getKey() {
		return key;
	}

	public void setKey(SelectionKey key) {
		this.key = key;
	}
}