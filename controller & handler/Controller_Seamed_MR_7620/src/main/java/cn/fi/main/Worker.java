/*   
 * Copyright (c) 2010-2020 Founder Ltd. All Rights Reserved.   
 *   
 * This software is the confidential and proprietary information of   
 * Founder. You shall not disclose such Confidential Information   
 * and shall use it only in accordance with the terms of the agreements   
 * you entered into with Founder.   
 *   
 */
package cn.fi.main;

import java.nio.channels.SocketChannel;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import cn.fi.obj.Agent;
import cn.fi.obj.MessageJob;
import cn.fi.obj.msg.Message;

/*
 * 对应一个消息接收处理单元，包含一个消息接收缓冲，和一个消息处理线程，单元的数量与服务器处理器个数相关
 * 
 * */
public class Worker {
	public static final int QUEUE_SIZE = 3000;// 消息接收缓冲大小
	private Log log = LogFactory.getLog(this.getClass());
	private MsgProcesser processer = null;// 消息处理线程
	private ExecutorService executor = null;
	private Master master = null;
	private BlockingQueue<MessageJob> queue = null;// 消息接收缓冲
	// 消息基础字段长度
	int baseLen = Master.MSG_LEN_LEN + Master.MSG_TYPE_LEN + Master.MSG_ID_LEN;

	public Worker(Master master, ExecutorService executor) {
		queue = new ArrayBlockingQueue<MessageJob>(QUEUE_SIZE);
		processer = new MsgProcesser();
		this.executor = executor;
		this.master = master;
	}

	/* 打印 */
	public void showView() {
		log.info("queue size is " + queue.size());
		for (MessageJob job : queue) {
			log.info("messageJob is " + job.toString());
		}
	}

	/* 将消息对象存入接收缓冲 */
	public void putMessage(MessageJob messageJob) {
		try {
			this.queue.put(messageJob);
			//log.info("queue.size: " + this.queue.size());
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	// 启动消息处理线程
	public void start() {
		executor.execute(processer);
	}

	/*
	 * 消息处理线程
	 * 
	 */
	private class MsgProcesser implements Runnable {

		public int checkLength(String message) {
			int baseLen = Master.MSG_LEN_LEN + Master.MSG_TYPE_LEN + Master.MSG_ID_LEN;
			int len = -1;
			if (message.length() < baseLen) {
				return -1;
			}
			try {
				len = Integer.parseInt(message.substring(Master.MSG_TYPE_LEN + Master.MSG_ID_LEN, baseLen).trim());
				if (len != message.length() - baseLen) {
					return -1;
				} else {
					return len;
				}
			} catch (Exception e) {
				e.printStackTrace();
				log.error(e);
				return -1;
			}
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub
			log.info("启动processMessage线程");
			while (true) {
				try {
					//log.info("processed starting...");
					MessageJob msgJob = null;
					try {
						//log.info("queue.size: " + queue.size());
						msgJob = queue.take();
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					String message = msgJob.getMessage();
					SocketChannel sc = msgJob.getSc();
					Agent agent = Master.agentManager.getAgentBySc(sc);
					String agentMsg = null;
					if(null != agent){
						agentMsg = "[" + agent.getIpAddress() + "/" + agent.getMacAddress() + "] ";
					}
					int msgLen = 0;
					if ((msgLen = checkLength(message)) < 0) {
						log.info(agentMsg + "msgLen<0, msg: " + message);
						continue;// drop
					}
					
					String type = message.substring(Master.MSG_ID_LEN, Master.MSG_ID_LEN + Master.MSG_TYPE_LEN);
					switch (type) {
					case Message.FNII_MSG_OK:
						master.updateAgentHeard(sc);
						master.processMsgOk(message, sc, msgLen);
						break;
					case Message.FNII_MSG_LOCAL_MAC:// first msg
						master.processMsgLocalMac(sc, message, msgLen);
						break;
					case Message.FNII_MSG_ERR:
						master.updateAgentHeard(sc);
						master.processMsgErr(message, sc, msgLen);
						break;
					case Message.FNII_MSG_NEW_STA:
						master.updateAgentHeard(sc);
						master.processMsgNewStation(message, sc, msgLen);
						break;
					case Message.FNII_MSG_DEL_STA:
						master.updateAgentHeard(sc);
						master.processMsgDelStation(message, sc, msgLen);
						break;
					case Message.FNII_MSG_FLOW_INFO:
						master.updateAgentHeard(sc);
						master.processMsgFlowInfo(message, sc, msgLen);
						break;
					case Message.FNII_MSG_PROBE_INFO:
						log.info("probe info is "+message);
						master.updateAgentHeard(sc);
						master.processMsgProbeInfo(message, sc, msgLen);
						break;
					case Message.FNII_MSG_IF_INFO:

						Master.consolePrintf("MSG_INFO: " + message);
						break;
					case Message.FNII_MSG_INTERFACE_NUM:// init agent
						master.updateAgentHeard(sc);
						master.processMsgInterfaceNum(message, sc, msgLen);
						break;
					case Message.FNII_MSG_HEART_BEAT:
						master.updateAgentHeard(sc);
						master.processMsgHeartBeat(sc, message, msgLen);
						break;
					default:// drop
						break;
					}
				}
				catch (Exception e) {
					log.info(e.getStackTrace());
					log.info(e.getMessage());
				}
			}
		}
	}
}