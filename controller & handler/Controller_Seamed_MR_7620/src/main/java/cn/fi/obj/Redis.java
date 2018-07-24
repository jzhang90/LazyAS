package cn.fi.obj;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.Transaction;
import cn.fi.main.Master;

/**
 * 用于redis操作的工具类
 */
public class Redis {
	
	/* 表名、属性名 */
	public static String AGENT_LIST = "agentList";
	// public static String AGENT_MAC = "";
	public static String AGENT_IP = "agentIp";
	public static String AGENT_CHANNEL = "agentChannel";
	public static String AGENT_MODE = "agentMode";
	public static String AGENT_VAPSET = "agentVapSet";
	public static String AGENT_STATIONSET = "agentStationSet";
	public static String AGENT_SSIDSET = "agentSsidSet";

	// public static String VAP_MAC = "";
	public static String VAP_MODE = "vapMode";
	public static String VAP_SSID = "vapSsid";
	public static String VAP_AGENT = "vapAgent";
	public static String VAP_STATION = "vapStation";
	public static String VAP_STATUS = "vapStatus";
	public static String VAP_CHANNEL = "vapChannel";

	public static String STATION_LIST = "stationList";
	// public static String STATION_IP = "";
	// public static String STATION_MAC = "";
	public static String STATION_STATUS = "staStatus";
	public static String STATION_SUM_UP_TRAFFIC = "staSumUpTraffic";
	public static String STATION_SUM_DOWN_TRAFFIC = "staSumDownTraffic";
	public static String STATION_DOWN_TRAFFIC = "staDownTraffic";
	public static String STATION_UP_TRAFFIC = "staUpTraffic";
	public static String STATION_VAPSET = "staVapContainerSet";
	public static String STATION_WORKING_VAP = "staWorkingVapContainer";
	
		
	
	/* 初始化redis数据库 */
	public static void initRedis() {		
		RedisPool.init();
	}

	/* 数据库中添加一个agent对象 */
	public static void addAgent(String macAddr, String ipAddr, String channel, String mode) {
		Jedis jedislocal = null;
		try{
			jedislocal =  RedisPool.newJedis();
			Transaction tx = jedislocal.multi(); // 通过事务来执行
			tx.sadd(AGENT_LIST, macAddr);
			tx.hset(macAddr, AGENT_IP, ipAddr);
			tx.hset(macAddr, AGENT_CHANNEL, channel + "");
			tx.hset(macAddr, AGENT_MODE, mode + "");
			tx.hset(macAddr, AGENT_VAPSET, macAddr + AGENT_VAPSET);// vapContainerSet
			tx.hset(macAddr, AGENT_SSIDSET, macAddr + AGENT_SSIDSET);
			tx.exec();
		}finally{
			RedisPool.closeJedis(jedislocal);
		}	
	}

	/* 数据库中删除一个agent对象 */
	public static void delAgent(String agentMacAddr) {
		Jedis jedislocal = null;
		try{
			jedislocal =  RedisPool.newJedis();
			if (jedislocal.exists(agentMacAddr)) {
				// String vapSet = jedis.hget(agentMacAddr, AGENT_VAPSET);
				String ssidSet = jedislocal.hget(agentMacAddr, AGENT_SSIDSET);
				Transaction tx = jedislocal.multi(); // 通过事务来执行
				// if (vapSet != null)
				// tx.del(vapSet);
				if (ssidSet != null)
					tx.del(ssidSet);
				tx.del(agentMacAddr);
				tx.srem(AGENT_LIST, agentMacAddr);
				tx.exec();
			}
		}finally{
			RedisPool.closeJedis(jedislocal);
		}
	}

	/* 添加一个station对象 */
	public static void addStation(String staMacAddr, String status) {
		Jedis jedislocal = null;
		try{
			jedislocal =  RedisPool.newJedis();
			Transaction tx = jedislocal.multi(); // 通过事务来执行
			tx.sadd(STATION_LIST, staMacAddr);
			// 初始化所有属性
			tx.hset(staMacAddr, STATION_STATUS, status);
			tx.hset(staMacAddr, STATION_SUM_UP_TRAFFIC, "0");
			tx.hset(staMacAddr, STATION_SUM_DOWN_TRAFFIC, "0");
			tx.hset(staMacAddr, STATION_UP_TRAFFIC, "0");
			tx.hset(staMacAddr, STATION_DOWN_TRAFFIC, "0");
			tx.hset(staMacAddr, STATION_VAPSET, staMacAddr + STATION_VAPSET);
			tx.hset(staMacAddr, STATION_WORKING_VAP, "");
			tx.exec();
		} finally {
			RedisPool.closeJedis(jedislocal);
		}
	}

	/* 删除一个station */
	public static void delStation(String staMacAddr) {
		Jedis jedislocal = null;
		try{
			jedislocal =  RedisPool.newJedis();
			if (jedislocal.exists(staMacAddr)) {
				String agentSet = jedislocal.hget(staMacAddr, STATION_VAPSET);
				Transaction tx = jedislocal.multi(); // 通过事务来执行
				if (agentSet != null)
					tx.del(agentSet);
				tx.del(staMacAddr);
				tx.srem(STATION_LIST, staMacAddr);
				tx.exec();
			}
		} finally {
			RedisPool.closeJedis(jedislocal);
		}		
	}

	/**/

	public static void addVapContainer(String agentMacAddr, String staMacAddr, String ssid) {// 设置vap属性
		String key = agentMacAddr + staMacAddr + ssid;
		Jedis jedislocal = null;
		try{
			jedislocal =  RedisPool.newJedis();
			Transaction tx = jedislocal.multi(); // 通过事务来执行
			// tx.hset(key, VAP_MODE, mode);
			tx.hset(key, VAP_AGENT, agentMacAddr);
			tx.hset(key, VAP_STATION, staMacAddr);
			tx.hset(key, VAP_SSID, ssid);
			tx.hset(key, VAP_STATUS, Master.OFF_LINE);
			// tx.hset(key, VAP_CHANNEL, channel);
			tx.exec();
		} finally {
			RedisPool.closeJedis(jedislocal);
		}
	}

	/* 删除一个vap对象 */
	public static void delVapContainer(String agentMacAddr, String staMacAddr, String ssid) {
		String key = agentMacAddr + staMacAddr + ssid;
		Jedis jedislocal = null;
		try{
			jedislocal =  RedisPool.newJedis();
			jedislocal.del(key);
		} finally {
			RedisPool.closeJedis(jedislocal);
		}
	}

}
