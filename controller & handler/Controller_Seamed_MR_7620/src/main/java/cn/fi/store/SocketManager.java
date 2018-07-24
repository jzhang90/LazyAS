package cn.fi.store;

import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;

import cn.fi.obj.SocketInfo;

/**
 * 存储socket对应的一些信息SocketInfo,主要为了处理套接字对应的上次未解析的消息残留（由于消息不完整）
 */
public class SocketManager {
	// 存储map，以socket为索引
	private final ConcurrentHashMap<SocketChannel, SocketInfo> socketMap = new ConcurrentHashMap<SocketChannel, SocketInfo>();

	// 存储视图，从视图删除
	public void putSocketInfo(SocketChannel sc, SocketInfo info) {
		socketMap.put(sc, info);
	}

	public void removeSocket(SocketChannel sc) {
		socketMap.remove(sc);
	}

	// 关闭套接字对应的selection key
	public void cancleKey(SocketChannel sc) {
		getSocketInfo(sc).getKey().cancel();
	}

	// 判断是否存储此socket
	public boolean isTracked(SocketChannel sc) {
		return socketMap.containsKey(sc);
	}

	public SocketInfo getSocketInfo(SocketChannel sc) {
		return socketMap.get(sc);
	}

}
