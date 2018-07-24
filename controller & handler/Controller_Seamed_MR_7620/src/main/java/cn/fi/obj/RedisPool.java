package cn.fi.obj;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cn.fi.main.Controller;
import cn.fi.main.Master;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.exceptions.JedisException;

public class RedisPool {
	public static Jedis jedis = null;
	protected static final Log log = LogFactory.getLog(Redis.class);
	private static JedisPoolConfig redisconfig;
	//protected String redisKey;
	private static JedisPool jedisPool;
	protected static Pipeline jpline;
	static int maxWaitMillis;
	static String redisIp;
	static int redisPort;	
	static String RedisPassword = "controller_redis";
	static int redisDataBaseNum=0;//默认database0
	
	//@SuppressWarnings("deprecation")
	public static void RedisPoolInit() {	
		jedisPool = new JedisPool(redisconfig, redisIp, redisPort, maxWaitMillis, 
									RedisPassword, redisDataBaseNum);						
		try {
			jedis = jedisPool.getResource();			
			jedis.flushDB();
			//Thread.sleep(60000);
			Master.consolePrintf("flushdb database" + redisDataBaseNum +" ok...");
			log.info("flushdb database" + redisDataBaseNum +" ok...");
		} catch (Exception e) {
			log.error("get redis resource fail:" + e.getMessage());
			if (jedis != null) {
				jedisPool.returnBrokenResource(jedis);
			}
			
			throw new IllegalArgumentException("can't get a redis conn from redis pool! ip:" + 
												redisIp + ", port:" + redisPort);
		}
	}
	
	public static void redisConfigInit() {
		// 设置池配置项值		
		redisconfig = new JedisPoolConfig();
		redisconfig.setMaxTotal(Integer.parseInt(Controller.RedisMaxActivity));
		redisconfig.setMaxIdle(Integer.parseInt(Controller.RedisMaxIdle));
		redisconfig.setMaxWaitMillis(Long.parseLong(Controller.RedisMaxWait)); // milli
																				// seconds
		redisconfig.setTestOnBorrow(Boolean.parseBoolean(Controller.RedisTestOnBorrow));
		redisconfig.setTestOnReturn(Boolean.parseBoolean(Controller.RedisOnReturn));

		redisIp = Controller.RedisServerIp;// "172.171.50.70";
		redisPort = Integer.parseInt(Controller.RedisServerPort);
		maxWaitMillis = Integer.parseInt(Controller.RedisMaxWait);
		redisDataBaseNum = Integer.parseInt(Controller.RedisDataBaseNum);
		Master.consolePrintf("redisIp/Port: " + redisIp + "/"+ redisPort+ " Config Set ok..." +			
				"\n\tRedisMaxActivity:"
				+ Integer.parseInt(Controller.RedisMaxActivity) + " RedisMaxIdle :"
				+ Integer.parseInt(Controller.RedisMaxIdle) + " RedisMaxWait :"
				+ Long.parseLong(Controller.RedisMaxWait) + " \n\tRedisTestOnBorrow :"
				+ Boolean.parseBoolean(Controller.RedisTestOnBorrow) + " RedisOnReturn: "
				+ Boolean.parseBoolean(Controller.RedisOnReturn) + " maxWaitMillis: " + Controller.RedisMaxWait);
	}
	
	public static void init(){				
		redisConfigInit();	// 设置池配置项值
		RedisPoolInit();	//创建线程池
	}
	
	public static void rebuildRedisPool() {
		try {
			jedisPool.returnBrokenResource(jedis);
			jedisPool.destroy();
		} catch (JedisException e) {
			log.error("jedis return broken resource fail:" + e.getMessage());
		} catch (Exception e1) {
			log.error("return broken resource fail:" + e1.getMessage());
		}
		
		try {
			Thread.sleep(5 * 1000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			log.error("Waiting 5 seconds interrupt.");
		}
		
		jedisPool = new JedisPool(redisconfig, redisIp, redisPort, maxWaitMillis);
		try {
			jedis = jedisPool.getResource();
		} catch (Exception e) {
			log.error("get redis resource fail:" + e.getMessage());
		}
		
		jpline = jedis.pipelined();
	}

	public static void reConnRedis() {
		try {
			jedisPool.returnResource(jedis);
		} catch (Exception e1) {
			log.error("return resource fail: " + e1.getMessage());
			try{
				jedisPool.returnBrokenResource(jedis);
			}catch (Exception e2){
				log.error("return broken resource fail: " + e1.getMessage());
				rebuildRedisPool();
			}			
		}
		try {
			Thread.sleep(1*1000);
		} catch (InterruptedException e1) {
			// TODO Auto-generated catch block
			log.error("Waiting 1 seconds interrupt.");
		}
		try {
			jedis = jedisPool.getResource();
			jpline = jedis.pipelined();
		} catch (JedisException e) {
			log.error("jedis get resource fail: " + e.getMessage());
			rebuildRedisPool();
		} catch (Exception e1) {
			log.error("get resource fail: " + e1.getMessage());
			rebuildRedisPool();
		}
	}
	
	public static Jedis newJedis() {
		Jedis localJedis = null;		
		try {
			localJedis = jedisPool.getResource();			
		} catch (Exception e) {
			log.error("newJedis fail: " + e.getMessage());
			localJedis = null;
			//reConnRedis(); //当前版本中redis仅供测试使用，为保证控制器正常工作，暂不需要重连接。
		}
		if (null == localJedis )
		{
			Master.consolePrintf("redis should be stopped.");
			log.error("redisPool get failed, and will stop the redis-write...");
			Master.REDIS_USED = false;//redis出错，关闭redis
			return jedis;	//暂时使用备用的redis连接
		}
		
		return localJedis;
	}
	
	public static void closeJedis(Jedis usedJedis) {
		try {
			jedisPool.returnResource(usedJedis);
		} catch (Exception e) {
			log.error("newJedis fail: " + e.getMessage());
			jedisPool.returnBrokenResource(usedJedis);
		}
	}
}
