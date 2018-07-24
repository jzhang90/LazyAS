/*   
 * Copyright (c) 2010-2020 Founder Ltd. All Rights Reserved.   
 *   
 * This software is the confidential and proprietary information of   
 * Founder. You shall not disclose such Confidential Information   
 * and shall use it only in accordance with the terms of the agreements   
 * you entered into with Founder.   
 *   
 */
package cn.fi.obj;

import java.nio.channels.SocketChannel;

/**
 * 消息接收缓冲中的消息对象
 * 
 */
public class MessageJob {
	private String message;// 消息字符串
	private SocketChannel sc;// 来自agent的IP地址

	public MessageJob(String message, SocketChannel sc) {
		super();
		this.message = message;
		this.sc = sc;
	}

	/* get、set方法 */
	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public SocketChannel getSc() {
		return sc;
	}

	public void setSc(SocketChannel sc) {
		this.sc = sc;
	}
}
