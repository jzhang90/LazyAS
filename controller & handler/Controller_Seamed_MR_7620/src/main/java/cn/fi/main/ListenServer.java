package cn.fi.main;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
//import java.text.SimpleDateFormat;
//import java.util.Date;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cn.fi.obj.Agent;
import cn.fi.obj.MessageJob;
import cn.fi.obj.SocketInfo;

/**
 * 控制器的消息监听处理部分，主要包含selector非阻塞IO及消息的基本解析操作
 * 
 */
public class ListenServer {
	private Server server = null;
	//private String fileName = "serverAddr";
	private ByteBuffer readBuffer = ByteBuffer.allocate(1024);
	private ExecutorService executor;
	private Log log = LogFactory.getLog(this.getClass());
	private Master master = null;
	private Worker[] workers = null;

	public ListenServer(Worker[] workers, ExecutorService executor) {
		this.master = Master.getInstance();
		this.server = new Server();
		this.workers = workers;
		this.executor = executor;
	}

	
	/* 从文件中获取服务器绑定地址和端口 */
	/*private InetSocketAddress getServerAddr(String fileName) {
		File file = new File(fileName);
		BufferedReader reader = null;
		String ip, port;
		InetSocketAddress socketAddress = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			if ((ip = reader.readLine()) != null && (port = reader.readLine()) != null) {
				socketAddress = new InetSocketAddress(ip, Integer.parseInt(port));
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (reader != null) {
				try {
					reader.close();
				} catch (IOException e1) {
				}
			}
		}
		return socketAddress;
	}*/

	/**
	 * 创建服务socket，注册期望事件，并启动消息监听线程
	 */
	public void start() {
		try {
			ServerSocketChannel ssc = ServerSocketChannel.open();
			ssc.configureBlocking(false);
			ServerSocket socket = ssc.socket();
			InetSocketAddress socketAddress = new InetSocketAddress(
					Controller.ControllerIP, Integer.parseInt(Controller.ControllerPort));
			socket.bind(socketAddress);
			ssc.register(server.getSelector(), SelectionKey.OP_ACCEPT);
			executor.execute(server);
			Master.consolePrintf("ControllerIp: " + Controller.ControllerIP + "/" + Controller.ControllerPort +
									" started ok...");	
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			log.error(e.getMessage());
		}
	}

	/**
	 * 消息监听线程，监听消息事件，并进行相应处理
	 * 
	 */
	public class Server implements Runnable {
		private Selector selector;

		public Server() {
			try {
				this.selector = Selector.open();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}

		public Selector getSelector() {
			return this.selector;
		}

		@Override
		public void run() {
			while (true) {
				try {
					this.selector.select();
					Set<SelectionKey> selectKeys = this.selector.selectedKeys();
					Iterator<SelectionKey> it = selectKeys.iterator();
					while (it.hasNext()) {
						SelectionKey key = it.next();
						it.remove();
						this.handle(key);// 处理事件
					}
				} catch (IOException e) {
					e.printStackTrace();
					log.info(e);
				}
			}
		}

		public void handle(SelectionKey key) throws IOException {
			try{
				if (key.isAcceptable()) {// 连接请求
					ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
					SocketChannel sc = null;
					try {
						sc = ssc.accept();
						Master.consolePrintf("Connection ap ip is " + sc.getRemoteAddress()
								+ " put socket in queue " + (sc.hashCode() % Controller.NUM_PROCESS_MESSAGE));
						log.info("Connection ap ip is " + sc.getRemoteAddress() + " put socket in queue "
								+ (sc.hashCode() % Controller.NUM_PROCESS_MESSAGE));
						sc.configureBlocking(false);
						SelectionKey key1 = sc.register(this.selector, SelectionKey.OP_READ);
						master.putSocketInfo(sc, new SocketInfo(key1));// 存入socketMap视图
					} catch (IOException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						log.info(e);
						key.cancel();
						sc.close();
					}
				} else if (key.isReadable()) {// 消息
					SocketChannel sc = (SocketChannel) key.channel();				
					int queueId = sc.hashCode() % Controller.NUM_PROCESS_MESSAGE;
					try {
						String agentMsg = null;
						Agent agent = Master.agentManager.getAgentBySc(sc);
						if(null != agent){//for debug
							agentMsg = "[" + agent.getIpAddress() + "/" + agent.getMacAddress() + "] ";
						}
						
						readBuffer.clear();
						int count = sc.read(readBuffer);
						if (count <= 0) {
							log.info(agentMsg + "socket read count < 0, and start to close the socket..");
							master.closeSocket(sc);
						} else {
							readBuffer.flip();
							byte[] bytes = new byte[readBuffer.remaining()];
							readBuffer.get(bytes, 0, bytes.length);
							String newMsg = new String(bytes).trim();// 新收到的消息
							if (newMsg.length() > 0) {
								/*
								 * 在系统实际运行过程中，大部分执行的是第一个if里面的语句，
								 * 只有ap第一次接入或者断开重连时才进入else 里面的语句执行
								 */
								SocketInfo socketInfo = master.getSocketInfo(sc);
								String message = socketInfo.getLegacy() + newMsg;
								socketInfo.setLegacy("");// 重新初始化遗留的消息
								String msgs[] = message.split(";");
								int len = msgs.length;
								if (newMsg.charAt(newMsg.length() - 1) != ';') {
									Master.consolePrintf("!!error. msg not end with ';'." +
											"agent:"+" msg is : "+message);
									log.info(agentMsg + "!!Info. msg not end with ';'. msg is : "+message);
									len -= 1;
									socketInfo.setLegacy(msgs[len]);
									log.info(agentMsg + "!!Info.msg saved to Legacy. msg: "+ socketInfo.getLegacy());
								}
								for (int i = 0; i < len; i++) {
									log.info("msg: " + msgs[i]);
									workers[queueId].putMessage(new MessageJob(msgs[i], sc));
								}
								
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
						log.info("!!handle error,and close the socket.");
						log.info(e);
						master.closeSocket(sc);
					}			
				}
			}
			catch(Exception e){
				log.info(e.getStackTrace());
				log.info(e.getMessage());
			}
		}
	}
}
