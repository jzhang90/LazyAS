package cn.fi.main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cn.fi.app.DualBandAccessControl;
import cn.fi.obj.Application;

/*
 * 控制器主类，完成以下工作：进行应用注册，启动消息监听线程，启动消息处理线程
 * 
 * */
public class Controller {
	public static final int NUM_PROCESSER = Runtime.getRuntime().availableProcessors();// 处理器数量
	// 根据NUM_PROCESSER，计算处理线程数量，此时主要线程为一个消息监听线程，若干个消息处理线程
	public static final int NUM_PROCESS_MESSAGE = NUM_PROCESSER > 1 ? NUM_PROCESSER - 1 : NUM_PROCESSER;
	private static Master master = null;
	private static Log log = LogFactory.getLog(Controller.class);
	static String ControllerIP = null;
	static String ControllerPort = null;
	//static boolean RedisEnable = false;
	
	//redis 配置
	public static String RedisServerIp = null;
	public static String RedisServerPort = null;
	public static String RedisMaxActivity;
	public static String RedisMaxIdle;
	public static String RedisMaxWait;
	public static String RedisTestOnBorrow;
	public static String RedisOnReturn;
	public static String RedisBulkTimeout;
	public static String RediSonceSize; 
	public static String RedisDataBaseNum;
	public static String showStationStore;
	public static String showStationStatistics;
	
	public static void main(String[] args) {
		master = Master.getInstance();
		try {
			//读配置文件
			readConfigfile();			
			// 启动服务器
			startServer();
			// 注册应用
			/*Application app = new AccessControlMultiApp();
			Application app1 = new HandoffApp();
			app1.setApplicationInterface(master);
			app.setApplicationInterface(master);
			master.getExecutor().execute(app);
			master.getExecutor().execute(app1);*/
			Application app = new DualBandAccessControl();
			app.setApplicationInterface(master);
			master.getExecutor().execute(app);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private static void readConfigfile() {
		// TODO Auto-generated method stub
		Properties properties = new Properties();
		File file = new File("controller.properties");
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(file);
		} catch (FileNotFoundException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}

		try {
			properties.load(fis);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		ControllerIP = properties.getProperty("ControllerIP");
		ControllerPort = properties.getProperty("ControllerPort");
		
		/*redis configuration*/
		String stRedisEnable = properties.getProperty("RedisEnable");
		if (stRedisEnable.equals("on")) {
			//if(true == checkRedisService())
			Master.REDIS_USED = true;
		}
		
		RedisServerIp = properties.getProperty("RedisServerIP");
		RedisServerPort = properties.getProperty("RedisServerPort");
		RedisDataBaseNum = properties.getProperty("RedisDataBase");
		log.info("ControllerIP/Port: " + ControllerIP + "/" + ControllerPort + "\nREDIS_USED : " + Master.REDIS_USED
				+ " RedisServerIp/Port" + RedisServerIp + "/" + RedisServerPort + "/" + RedisDataBaseNum);

		RedisMaxActivity = properties.getProperty("jedis.pool.maxTotal");
		RedisMaxIdle = properties.getProperty("jedis.pool.maxIdle");
		RedisMaxWait = properties.getProperty("jedis.pool.maxWaitMillis");
		RedisTestOnBorrow = properties.getProperty("jedis.pool.testOnBorrow");
		RedisOnReturn = properties.getProperty("jedis.pool.testOnReturn");
		RedisBulkTimeout = properties.getProperty("redis.timeout");
		RediSonceSize = properties.getProperty("redis.once.deal.mum");
		
		/*log configuration*/
		showStationStore = properties.getProperty("showStationStore");
		showStationStatistics = properties.getProperty("showStationStatistics");
		Master.showStoreView = Boolean.parseBoolean(showStationStore);
		Master.showStatisticsView = Boolean.parseBoolean(showStationStatistics);
		log.info("showStoreView : "+ Master.showStoreView  + " showStatisticsView: " + Master.showStatisticsView );
	}

	static void startServer() {
		// 采用线程池的方式启动
		ExecutorService executor = Executors.newFixedThreadPool(NUM_PROCESS_MESSAGE + 1);
		Worker[] workers = new Worker[NUM_PROCESS_MESSAGE];
		// 启动多个消息处理线程，每个线程对应一个消息队列
		for (int i = 0; i < NUM_PROCESS_MESSAGE; i++) {
			workers[i] = new Worker(master, executor);
			workers[i].start();  //消息处理线程
			log.info("start workerthread " + i);
		}

		/* 创建并启动消息监听线程，即生产者线程 */
		ListenServer processServer = new ListenServer(workers, executor);
		processServer.start();//消息接收线程
		master.init();// 初始化master,包括redis和老化线程
	}

}
