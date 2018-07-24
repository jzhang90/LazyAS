package cn.fi.store;

import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.fi.main.Master;
import cn.fi.obj.Agent;
import cn.fi.obj.Redis;

/**
 * Agent对应的管理视图 ，主要有两个存储map：AP的mac地址索引的map和当前socket索引的map
 * 
 */
public class AgentManager {
	// 存储map，mac地址索引
	private final ConcurrentHashMap<String, Agent> agentMap = new ConcurrentHashMap<String, Agent>();
	/* 当前agent对应的socket索引，主要是为了处理AP连接后未汇报mac地址时对其对应agent的存储 */
	private final ConcurrentHashMap<SocketChannel, Agent> sc2AgentMap = new ConcurrentHashMap<SocketChannel, Agent>();

	// 判断视图中是否包含此mac地址
	public boolean isTracked(String agentMacAddr) {
		return agentMap.containsKey(agentMacAddr);
	}

	// 判断视图中是否包含此socket
	public boolean isTrackedSc(SocketChannel sc) {
		return sc2AgentMap.containsKey(sc);
	}

	// 加入视图、从视图删除方法
	public void removeAgentBySc(SocketChannel sc) {
		sc2AgentMap.remove(sc);
	}

	public void putAgentBySc(SocketChannel sc, Agent agent) {
		sc2AgentMap.put(sc, agent);
	}

	public void removeAgent(String agentMacAddr) {
		agentMap.remove(agentMacAddr);
		if (Master.REDIS_USED) {
			Redis.delAgent(agentMacAddr);
		}
	}

	public void addAgent(String agentMacAddr, Agent agent) {
		agentMap.put(agentMacAddr, agent);
		if (Master.REDIS_USED) {
			String channel = String.valueOf(this.getAgent(agentMacAddr).getChannel());
			String mode = this.getAgent(agentMacAddr).getMode();
			Redis.addAgent(agentMacAddr, agent.getIpAddress() + "", channel, mode);
		}
	}

	/* get set方法 */
	public Agent getAgentBySc(SocketChannel sc) {
		return sc2AgentMap.get(sc);
	}

	public Map<String, Agent> getAgents() {
		return Collections.unmodifiableMap(agentMap);
	}

	public Map<SocketChannel, Agent> getScAgents() {
		return Collections.unmodifiableMap(sc2AgentMap);
	}

	public Agent getAgent(final String agentMacAddr) {
		return agentMap.get(agentMacAddr);
	}

}
