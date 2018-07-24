package cn.fi.obj;

import cn.fi.main.Master;
import redis.clients.jedis.Jedis;

/*
 * 用于保存agent/station/ssid之间的对应关系，在addAgent的时候创建
 * */
public class VapContainer {
	private String BSSID = null;// bssid mac
	private String ssid = null;// 对应的ssid名称
	private Agent agent = null;// 所属agent
	private Station station = null;// 对应station
	private String status = Master.OFF_LINE;// 当前的状态，分为两种：offline（表示终端是已分配未关联状态）和online（表示终端已分配已关联）
	private long savedProbeHeard = 0;// 保存上次心跳时间。时间戳标示，用于Master中的AgeTask老化vap
	private long lastProbeHeard = 0;// 心跳时间戳
	
	public VapContainer() {
		super();
	}

	public VapContainer(Station station, Agent agent, String ssid) {
		super();
		this.agent = agent;
		this.ssid = ssid;
		this.station = station;
	}

	@Override
	public String toString() {
		return "Vap [BSSID=" + BSSID +
				// ", mode=" + mode +
				", ssid=" + ssid + ", status=" + status + ", savedProbeHeard=" + savedProbeHeard + ", lastProbeHeard="
				+ lastProbeHeard + "]";
	}

	/* get set方法 */
	public long getLastProbeHeard() {
		return lastProbeHeard;
	}

	public void setLastProbeHeard(long lastProbeHeard) {
		this.lastProbeHeard = lastProbeHeard;
	}

	public long getSavedProbeHeard() {
		return savedProbeHeard;
	}

	public void setSavedProbeHeard(long savedProbeHeard) {
		this.savedProbeHeard = savedProbeHeard;
	}

	public Station getStation() {
		return station;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {		
		if(this.status == Master.ON_LINE && status.equals(Master.OFF_LINE)){//on->off
			this.agent.delStaCountOnline();
		}else if(this.status == Master.OFF_LINE && status.equals(Master.ON_LINE)){ //off->on
			this.agent.addStaCountOnline();
		}
		
		this.status = status;
		if (Master.REDIS_USED) {
			String agentMacAddr = this.agent.getMacAddress();
			String staMacAddr = this.station.getMacAddress();
			String ssid = this.getSsid();
			String key = agentMacAddr + staMacAddr + ssid;
			Jedis jedislocal = null;
			try{
				jedislocal =  RedisPool.newJedis();
				if (jedislocal.exists(key)) {
					jedislocal.hset(key, Redis.VAP_STATUS, status);
				}
			}finally{
				RedisPool.closeJedis(jedislocal);
			}
		}
	}

	public void setStation(Station station) {
		this.station = station;
	}

	public String getBSSID() {
		return BSSID;
	}

	public void setBSSID(String BSSID) {
		this.BSSID = BSSID;
	}

	public String getSsid() {
		return ssid;
	}

	public void setSsid(String ssid) {
		this.ssid = ssid;
	}

	public Agent getAgent() {
		return agent;
	}
}
