package cn.fi.obj;

import java.net.InetAddress;
import java.nio.channels.SocketChannel;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import cn.fi.main.Master;

/**
 * 对应一个AP，主要包含Mac地址、IP地址、Vap、状态，心跳等信息。 每个Agent可以有多个Vap，状态分为上线、下线。
 * 当AP起2.4G和5G时，相当于2个agent.
 * 
 */
public class Agent {
	private int testNum=0;
	private int staCountIdle = 32;//
	private int staCountOnline = 0;//
	private int statCountSum = 32;// 总数
	private String mode;// 2.4/5g
	private int channel; // 信道
	private String status;// 状态上线online或者下线offline
	private InetAddress ipAddress;// agent对应ap的IP
	private String macAddress;
	private int currentId = 0;// 用于生成发送给此agent对应的消息的ID
	private Object idLock = new Object();
	private SocketChannel sc;// 对应的套接字
	private long lastHeard = 0;// 上一次心跳时间
	private long lastHeartBeat = 0;//heartbeat消息
	private ConcurrentHashSet<String> BSSIDSet = new ConcurrentHashSet<String>(32);// 支持的BSSID集合
	private ConcurrentHashSet<String> SsidSet = new ConcurrentHashSet<String>(32);// 支持的BSSID集合
	private ConcurrentHashMap<String, VapContainer> vapContainerSet = new ConcurrentHashMap<String, VapContainer>(48);
	private Log log = LogFactory.getLog(this.getClass());// 日志
	private long sumUpTraffic = 0;
	private long sumDownTraffic = 0;
	private Object trafficLock = new Object();
	String agentMsg = "["+this.getIpAddress()+"/"+this.getMacAddress()+ "] ";
	//监控心跳连接
	public void setLastHeartBeat(long heartBeatTime){
		this.lastHeartBeat = heartBeatTime;
	}
	
	public long getLastHeartBeat(){
		return this.lastHeartBeat;
	}
	
	public void setChannel(int channel) {
		this.channel = channel;
		if (Master.REDIS_USED) {
			Jedis jedislocal = null;
			try{
				jedislocal =  RedisPool.newJedis();
				if (jedislocal.exists(macAddress)) {
					jedislocal.hset(macAddress, Redis.AGENT_CHANNEL, channel + "");
				}
			}finally{
				RedisPool.closeJedis(jedislocal);
			}
		}
	}

	public int getChannel() {
		return this.channel;
	}

	public String getMode() {
		return this.mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
		if (Master.REDIS_USED) {
			Jedis jedislocal = null;
			try{
				jedislocal =  RedisPool.newJedis();
				if (jedislocal.exists(macAddress)) {
					jedislocal.hset(macAddress, Redis.AGENT_MODE, mode + "");
				}
			}finally{
				RedisPool.closeJedis(jedislocal);
			}
		}
	}

	// ap上报的可分配的总数
	public int getStaCountSum() {
		return statCountSum;
	}

	public void setStaCountSum(int num) {
		this.statCountSum = num;
	}

	public int getStaCountOnline() {		
		return getVapContainerOnline();	//为了效率，不使用此方法。	
		
		//return staCountOnline;
	}

	public void delStaCountOnline() {
		this.staCountOnline--;
	}

	public void addStaCountOnline() {
		this.staCountOnline++;
	}

	// 空闲可下发额度。下发之后，idle--;
	// set函数初始化为32
	public void setStaCountIdle(int num) {
		this.staCountIdle = num;
	}

	public int getStaCountIdle() {		
		///* //为了效率，采用本地保存计数的形式	
		if (this.staCountIdle <= 0) {
			this.testNum++;
			if(this.testNum % 10 == 0){
				int vapIdle = this.getVapContainerIdle();
				Master.consolePrintf("staCountIdle <= 0!!!!, "
									+ " staCountIdle:" + this.staCountIdle 
									+ " vapIdle: " + vapIdle
									+ " online: "+ this.staCountOnline 
									+ " agent: " + this.getIpAddress()+"/"+ this.getMacAddress()+"/"+ this.getMode()
									+ " testNum: " + this.testNum);
				Master.consolePrintf("VapCountIdle: " + this.getVapContainerIdle()
									+ " agent: " + this.getIpAddress()+"/"+ this.getMacAddress()+"/"+ this.getMode());
				Master.showAgentVapState(this);
			}
		}
		//return staCountIdle;				
		//*/
		
		// 特殊情况idle计数为负值，此时检查vap数目
		return this.getVapContainerIdle();//此方法为根本方法，为效率考虑不建议使用。
	}

	public void delStaCountIdle(String key) {
		this.staCountIdle--;		
	}

	public void addStaCountIdle(String key) {		
		this.staCountIdle++;		
	}
	// set

	public int getVapContainerIdle() {
		int vapNum = 0;
		Iterator<Entry<String, VapContainer>> it = this.vapContainerSet.entrySet().iterator();
		while (it.hasNext()) {
			it.next();
			vapNum++;
		}
		if(vapNum > 32){
			Master.consolePrintf("!!VapNume > 32, vapnum: " + vapNum);
			log.info(agentMsg+ "!!VapNume > 32, vapnum: " + vapNum);
			return 0;
		}
		return (32 - vapNum);
	}

	public int getVapContainerOnline(){
		int num = 0;
		Iterator<Entry<String, VapContainer>> it = this.vapContainerSet.entrySet().iterator();
		//Iterator<String, VapContainer> it = this.vapContainerSet.iterator();
		while (it.hasNext()) {
			VapContainer vapContainer = it.next().getValue();
			if(vapContainer.getStatus().equals(Master.ON_LINE)){
				num++;
			}			
		}
		return num;
	}
	
	/* 生成下一条消息的Id标识，以1000循环 */
	public int getMsgId() {
		synchronized (idLock) {
			// ID长度为4位，超出后重新从0开始。 因为消息ID有可能超越int表示的范围，所以没有使用取余 ，而是超越10000后直接清零
			return ++currentId > 10000 ? currentId -= 10000 : currentId;
		}
	}

	/// *
	public Set<String> getBSSIDSet() {
		return Collections.unmodifiableSet(BSSIDSet.getValues());
	}

	public void addBSSIDSet(String BSSID) {
		BSSIDSet.add(BSSID);
	}

	public void removeBSSIDSet(String BSSID) {
		BSSIDSet.remove(BSSID);
	}// */

	public void addSsidSet(String ssid) {
		SsidSet.add(ssid);
		if (Master.REDIS_USED) {
			Jedis jedislocal = null;
			try{
				jedislocal =  RedisPool.newJedis();
				String agentSsidSet = jedislocal.hget(macAddress, Redis.AGENT_SSIDSET);
				if (agentSsidSet != null) {
					jedislocal.sadd(agentSsidSet, ssid);
				}
			}finally{
				RedisPool.closeJedis(jedislocal);
			}
		}
	}

	public void removeSsidSet(String ssid) {
		SsidSet.remove(ssid);
		if (Master.REDIS_USED) {
			Jedis jedislocal = null;
			try{
				jedislocal =  RedisPool.newJedis();
				String agentSsidSet = jedislocal.hget(macAddress, Redis.AGENT_SSIDSET);
				if (agentSsidSet != null) {
					jedislocal.srem(agentSsidSet, ssid);
				}
			}finally{
				RedisPool.closeJedis(jedislocal);
			}
		}
	}

	public Set<String> getSsidSet() {
		return Collections.unmodifiableSet(SsidSet.getValues());
	}

	/// * get set方法 */
	public Set<Station> getStationSet() {
		ConcurrentHashSet<Station> stationSet = new ConcurrentHashSet<Station>(32);
		for (VapContainer vapContainer : this.vapContainerSet.values()) {
			Station station = vapContainer.getStation();
			if (station != null)
				stationSet.add(station);
		}
		return Collections.unmodifiableSet(stationSet.getValues());
	}

	/* 从vapContainerSet删除一个vap */
	public void removeVapContainer(VapContainer vapContainer) {
		if (vapContainer != null) {					
			String agentMacAddr = vapContainer.getAgent().getMacAddress();
			String staMacAddr = vapContainer.getStation().getMacAddress();
			String ssid = vapContainer.getSsid();
			String key = agentMacAddr + staMacAddr + ssid;
			if(this.vapContainerSet.containsKey(key)){
				this.addStaCountIdle(key);
				this.vapContainerSet.remove(key);
			}else{
				Master.consolePrintf("!!error. vapContainerSet cannot remove :" + key 
						+ ", agent: " + this.getIpAddress()+ "/"+this.getMacAddress()+"/"+this.getMode());
				//for debug print
				Master.consolePrintf("-------------------key set----start------------------------");
				for(Entry<String, VapContainer> entry : this.vapContainerSet.entrySet()){
					 Master.consolePrintf("key: " + entry.getKey());
				}
				Master.consolePrintf("-------------------key set-----end-------------------------");
			}						
				
			if (Master.REDIS_USED) {
				Jedis jedislocal = null;
				try{
					jedislocal =  RedisPool.newJedis();				
					String agentVapContainierSet = jedislocal.hget(macAddress, Redis.AGENT_VAPSET);
					if (agentVapContainierSet != null) {
						jedislocal.srem(agentVapContainierSet, key);
					}
				}finally{
					RedisPool.closeJedis(jedislocal);
				}
			}
		}
	}

	public VapContainer getVapContainer(Station station, String ssid) {		
		String staMacAddr = station.getMacAddress();
		String key = this.macAddress + staMacAddr + ssid;	
		return this.vapContainerSet.get(key);		
	}

	/* 添加一个vap到vapSet */
	public void addVapContainer(VapContainer vapContainer) {		
		String agentMacAddr = vapContainer.getAgent().getMacAddress();
		String staMacAddr = vapContainer.getStation().getMacAddress();
		String ssid = vapContainer.getSsid();
		String key = agentMacAddr + staMacAddr + ssid;	
		
		Master.consolePrintf("statCountIdle: " + this.getStaCountIdle()
							+ "agent:" + this.getIpAddress() + "/" + this.getMacAddress()+ "/"+ this.getMode());
		if(!this.vapContainerSet.containsKey(key)){
			this.delStaCountIdle(key);
			this.vapContainerSet.put(key, vapContainer);
		}else{
			Master.consolePrintf("!!error. vapContainerSet alread has :" + key);
		}			
		Master.consolePrintf("statCountIdle: " + this.getStaCountIdle()
		+ "agent:" + this.getIpAddress() + "/" + this.getMacAddress()+ "/"+ this.getMode());

		if (Master.REDIS_USED) {
			Jedis jedislocal = null;
			try{
				jedislocal =  RedisPool.newJedis();
				String agentVapContainerSet = jedislocal.hget(macAddress, Redis.AGENT_VAPSET);
				if (agentVapContainerSet != null) {
					jedislocal.sadd(agentVapContainerSet, key);
					Transaction tx = jedislocal.multi(); // 通过事务来执行
					// tx.hset(key, VAP_MODE, mode);
					tx.hset(key, Redis.VAP_AGENT, agentMacAddr);
					tx.hset(key, Redis.VAP_STATION, staMacAddr);
					tx.hset(key, Redis.VAP_SSID, ssid);
					tx.hset(key, Redis.VAP_STATUS, Master.OFF_LINE);//下发时为offline,等上线时更新为online
					tx.hset(key, Redis.VAP_MODE, this.getMode());
					tx.exec();
				}
			}finally{
				RedisPool.closeJedis(jedislocal);
			}
		}
	}

	public String getMacAddress() {
		return macAddress;
	}

	public void setMacAddress(String macAddress) {
		this.macAddress = macAddress;
	}

	public String getStatus() {
		if (System.currentTimeMillis() - lastHeard > Master.AGENT_TIME_OUT * 1000) {
			status = Master.OFF_LINE;
		} else {
			status = Master.ON_LINE;
		}
		return status;
	}

	public InetAddress getIpAddress() {
		return ipAddress;
	}

	public void setIpAddress(InetAddress ipAddress) {
		this.ipAddress = ipAddress;
	}

	public SocketChannel getSc() {
		return sc;
	}

	public void setSocketChannel(SocketChannel sc) {
		this.sc = sc;
	}

	public long getLastHeard() {
		return lastHeard;
	}

	public void setLastHeard(long lastHeard) {
		this.lastHeard = lastHeard;
	}

	public void addSumTraffic(long upTraffic, long downTraffic) {
		synchronized (trafficLock) {
			this.sumUpTraffic += upTraffic;
			this.sumDownTraffic += downTraffic;
		}
	}

	/**
	 * @return sumUpTraffic
	 */
	public long getSumUpTraffic() {
		return sumUpTraffic;
	}

	/**
	 * @return sumDownTraffic
	 */
	public long getSumDownTraffic() {
		return sumDownTraffic;
	}

	@Override
	public String toString() {
		return "Agent [status=" + getStatus() + ", ipAddress=" + ipAddress + ", macAddress=" + macAddress
				+ ", currentId=" + currentId + ", sc=" + sc + ", lastHeard=" + lastHeard + "]";
	}

	public ConcurrentHashMap<String, VapContainer> getVapContainerSet() {
		// TODO Auto-generated method stub
		return vapContainerSet;
	}

	// */
	/**
	 * 解除station与vap视图中的对应关系
	 * 
	 * 新: agentManager.agent清空； 遍历station，station.agentSet清除agent
	 */
	public void reset(Master master) {
		//检查心跳是否超时
		long heartBeatInterval = System.currentTimeMillis() - this.getLastHeartBeat();
		if(heartBeatInterval > Master.AGENT_HEARET_BEAT_AGING * 1000){
			log.info(agentMsg+ "Error! last heartbeat receved "+ heartBeatInterval/1000 + "(>10s) ago!");
		}
		
		log.error(agentMsg+ "!!!!!!!reset agent: " +this.getIpAddress()+"/"+ this.getMacAddress()+ "/"+ this.getMode()+ 
				" heartBeatInterval: " + heartBeatInterval/1000+ "(s)");
		Master.consolePrintf("!!!!!!!reset agent: " +this.getIpAddress()+"/"+ this.getMacAddress()+ "/"+ this.getMode()+ 
							" heartBeatInterval: " + heartBeatInterval/1000+ "(s)");
		
		Iterator<Entry<String, VapContainer>> it = this.vapContainerSet.entrySet().iterator();
		while (it.hasNext()) {
			VapContainer vapContainer = it.next().getValue();
			Station station = vapContainer.getStation();
			if (station != null) {
				synchronized (station) {
					if (station.getVapContainerSet().contains(vapContainer)) {
						station.removeVapContainer(vapContainer);
						if (station.getWorkingVapContainer() == vapContainer) {
							station.setStatus(Master.OFF_LINE);
							station.setWorkingVapContainer(null);
							station.updateSumTraffic();
						}
					}
					this.removeVapContainer(vapContainer);
				}
			} else {				
				synchronized (this) {			
					this.removeVapContainer(vapContainer);
				}
			}
		}

		setLastHeard(0);// 重置心跳，标示离线
		setSocketChannel(null);
	}
}
