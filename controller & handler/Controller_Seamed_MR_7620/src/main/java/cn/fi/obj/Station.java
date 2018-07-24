package cn.fi.obj;

import java.net.InetAddress;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import cn.fi.main.Master;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;

/**
 * 对应一个终端设备，主要包含含Mac地址、IP地址，可用Vap集合，在线Vap，流量统计，状态、心跳信息等。
 * 每个station可以有多个可选择的Vap，但是在线的只有一个，状态分为上线、下线。
 * 
 */
public class Station {
	private int[] addAgentMark = new int[2];// for debug
	private String macAddress;
	private InetAddress ipAddress;
	private long lastHeard = 0;// 心跳时间戳
	private String lastheardMode;// 和心跳同时配合使用。
	private String status;// offline（表示离线状态）和online（表示在线状态）
	/* 流量信息 */
	private long sumUpTraffic;// 当前上线流量
	private long sumDownTraffic;// 当前下行流量
	private long upTraffic;// 累计上行流量统计总数
	private long downTraffic;// 累计下行流量统计总数
	private VapContainer workingVapContainer = null;// 若在线则为上线的vap
	private ConcurrentHashMap<String, VapContainer> vapContainerSet = new ConcurrentHashMap<String,VapContainer>(32);

	public Station(String macAddress, String status) {
		this.macAddress = macAddress;
		this.upTraffic = 0;
		this.downTraffic = 0;
		this.sumDownTraffic = 0;
		this.sumUpTraffic = 0;
		this.status = status;
	}

	public void addAddAgentMark(String mode, boolean isRoam) {// for
																// debug，记录下发次数。正常情况下只下发1次。(漫游除外)
		if (isRoam) {
			return;
		}
		if (mode.equals(Master.MODE_5G))
			this.addAgentMark[1]++;
		else if (mode.equals(Master.MODE_2d4G)) {
			this.addAgentMark[0]++;
		}
	}

	public void delAddAgentMark(String mode, boolean isRoam) {// for
																// debug，记录下发次数。正常情况下只下发1次。(漫游除外)
		if (isRoam) {
			return;
		}
		if (mode.equals(Master.MODE_5G))
			this.addAgentMark[1]--;
		else if (mode.equals(Master.MODE_2d4G)) {
			this.addAgentMark[0]++;
		}
	}

	public int getAddAgentMark(String mode) {// 此处有误，需要区分agent！！！
		if (mode.equals(Master.MODE_5G))
			return this.addAgentMark[1];
		else if (mode.equals(Master.MODE_2d4G)) {
			return this.addAgentMark[0];
		}
		return 0;
	}

	public void setLastHeard(long lastHeard) {
		this.lastHeard = lastHeard;
	}

	public void setLastHeardMode(String mode) {
		this.lastheardMode = mode;
	}

	public String getLastHeardMode() {
		return this.lastheardMode;
	}

	public void updateSumTraffic() {
		this.sumDownTraffic += this.downTraffic;
		this.sumUpTraffic += this.upTraffic;
		this.downTraffic = 0;
		this.upTraffic = 0;
		if (Master.REDIS_USED) {
			Jedis jedislocal = null;
			try{
				jedislocal =  RedisPool.newJedis();			
				if (jedislocal.exists(macAddress)) {
					jedislocal.hset(macAddress, Redis.STATION_SUM_UP_TRAFFIC, sumDownTraffic + "");
					jedislocal.hset(macAddress, Redis.STATION_SUM_DOWN_TRAFFIC, sumUpTraffic + "");
					jedislocal.hset(macAddress, Redis.STATION_UP_TRAFFIC, downTraffic + "");
					jedislocal.hset(macAddress, Redis.STATION_DOWN_TRAFFIC, upTraffic + "");
				}
			}finally{
				RedisPool.closeJedis(jedislocal);
			}
		}
	}

	public Set<String> getSsidSet() {
		ConcurrentHashSet<String> ssidSet = new ConcurrentHashSet<String>(3);// 可上线SSID集合
		for (VapContainer vapContainer : vapContainerSet.values()) {
			if (vapContainer.getStation() == this) {
				ssidSet.add(vapContainer.getSsid());
			}
		}

		return Collections.unmodifiableSet(ssidSet.getValues());
	}

	public void addVapContainer(VapContainer vapContainer) {
		String agentMacAddr = vapContainer.getAgent().getMacAddress();
		String staMacAddr = vapContainer.getStation().getMacAddress();
		String ssid = vapContainer.getSsid();
		String key = agentMacAddr + staMacAddr + ssid;
		vapContainerSet.put(key, vapContainer);

		if (Master.REDIS_USED) {
			Jedis jedislocal = null;
			try{
				jedislocal =  RedisPool.newJedis();	
				String staVapContainerSet = jedislocal.hget(macAddress, Redis.STATION_VAPSET);
				if (staVapContainerSet != null) {
					jedislocal.sadd(staVapContainerSet, key);
					Transaction tx = jedislocal.multi(); // 通过事务来执行
					tx.hset(key, Redis.VAP_AGENT, agentMacAddr);
					tx.hset(key, Redis.VAP_STATION, staMacAddr);
					tx.hset(key, Redis.VAP_SSID, ssid);
					tx.hset(key, Redis.VAP_STATUS, Master.OFF_LINE);
					tx.exec();
				}
			}finally{
				RedisPool.closeJedis(jedislocal);
			}
		}
	}

	public void removeVapContainer(VapContainer vapContainer) {		
		if (vapContainer != null) {
			String agentMacAddr = vapContainer.getAgent().getMacAddress();
			String staMacAddr = vapContainer.getStation().getMacAddress();
			String ssid = vapContainer.getSsid();
			String key = agentMacAddr + staMacAddr + ssid;
			vapContainerSet.remove(key);
			if (Master.REDIS_USED) {
				Jedis jedislocal = null;
				try{
					jedislocal =  RedisPool.newJedis();	
					String staVapContainierSet = jedislocal.hget(macAddress, Redis.STATION_VAPSET);
					if (staVapContainierSet != null) {
						jedislocal.srem(staVapContainierSet, key);
					}
				}finally{
					RedisPool.closeJedis(jedislocal);
				}
			}
		}
	}

	public VapContainer getVapContainer(Agent agent, String ssid) {		
		String key = agent.getMacAddress() + this.macAddress + ssid;
		return this.vapContainerSet.get(key);		
	}

	public Set<Agent> getAgentSet() {
		ConcurrentHashSet<Agent> agentSet = new ConcurrentHashSet<Agent>();
		for (VapContainer vapContainer : vapContainerSet.values()) {
			if (vapContainer.getStation() == this) {
				agentSet.add(vapContainer.getAgent());
			}
		}
		return Collections.unmodifiableSet(agentSet.getValues());
	}

	/* get set方法 */
	public String getMacAddress() {
		return macAddress;
	}

	public String getWorkingSsid() {
		if (this.workingVapContainer != null)
			return this.workingVapContainer.getSsid();
		else
			return null;
	}

	public void setMacAddress(String macAddress) {
		this.macAddress = macAddress;
	}

	public InetAddress getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(InetAddress ipAddress) {
		this.ipAddress = ipAddress;
	}

	public long getLastHeard() {
		return lastHeard;
	}

	public void setStatus(String status) {
		this.status = status;
		if (Master.REDIS_USED) {
			Jedis jedislocal = null;
			try{
				jedislocal =  RedisPool.newJedis();	
				if (jedislocal.exists(macAddress)) {
					jedislocal.hset(macAddress, Redis.STATION_STATUS, status);
				}
			}finally{
				RedisPool.closeJedis(jedislocal);
			}
		}
	}

	public String getStatus() {// idle/online/offline
		return status;
	}

	public long getUpTraffic() {
		return upTraffic;
	}

	public void setUpTraffic(long upTraffic) {
		this.upTraffic = upTraffic;
		if (Master.REDIS_USED) {
			Jedis jedislocal = null;
			try{
				jedislocal =  RedisPool.newJedis();	
				if (jedislocal.exists(macAddress)) {
					jedislocal.hset(macAddress, Redis.STATION_UP_TRAFFIC, upTraffic + "");
				}
			}finally{
				RedisPool.closeJedis(jedislocal);
			}
		}
	}

	public long getDownTraffic() {
		return downTraffic;
	}

	public void setDownTraffic(long downTraffic) {
		this.downTraffic = downTraffic;
		if (Master.REDIS_USED) {
			Jedis jedislocal = null;
			try{
				jedislocal =  RedisPool.newJedis();
				if (jedislocal.exists(macAddress)) {
					jedislocal.hset(macAddress, Redis.STATION_DOWN_TRAFFIC, downTraffic + "");
				}
			}finally{
				RedisPool.closeJedis(jedislocal);
			}
		}
	}

	public long getSumUpTraffic() {
		return sumUpTraffic;
	}

	public long getSumDownTraffic() {
		return sumDownTraffic;
	}

	@Override
	public String toString() {
		// String agentSetString = null;
		// if (agentSet != null)
		// agentSetString = agentSet.getValues().toString();
		return "Station [macAddress=" + macAddress + ", ipAddress=" + ipAddress + ", lastHeard=" + lastHeard
				+ ", status=" + status + ", sumUpTraffic=" + sumUpTraffic + ", sumDownTraffic=" + sumDownTraffic
				+ ", upTraffic=" + upTraffic + ", downTraffic=" + downTraffic + ", workingVapContainer="
				+ workingVapContainer + ", agentSet=" +
				// agentSetString +
				"]";
	}

	public void setWorkingVapContainer(VapContainer vapContainer) {
		this.workingVapContainer = vapContainer;
	}

	public VapContainer getWorkingVapContainer() {
		return workingVapContainer;
	}

	public ConcurrentHashMap<String, VapContainer> getVapContainerSet() {
		return this.vapContainerSet;
	}

}
