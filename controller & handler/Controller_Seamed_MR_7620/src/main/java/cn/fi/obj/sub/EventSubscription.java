/*   
 * Copyright (c) 2010-2020 Founder Ltd. All Rights Reserved.   
 *   
 * This software is the confidential and proprietary information of   
 * Founder. You shall not disclose such Confidential Information   
 * and shall use it only in accordance with the terms of the agreements   
 * you entered into with Founder.   
 *   
 */
package cn.fi.obj.sub;

/**
 * 事件订阅类,用于应用向系统注册订阅，需要在订阅中指明订阅消息的终端地址、消息类型和终端状态
 * 
 */
public class EventSubscription {
	private String staMacAddress;// 消息來自哪個station的mac地址 或者*表示所有
	private String msgType;// 订阅的消息类型 具体类型见Message中的定义
	private String status;// 订阅消息来自的station的当前状态

	public EventSubscription(String staMacAddress, String msgType, String status) {
		super();
		this.staMacAddress = staMacAddress;
		this.msgType = msgType;
		this.status = status;
	}

	/* get set方法 */
	public String getClient() {
		return staMacAddress;
	}

	public void setClient(String staMacAddress) {
		this.staMacAddress = staMacAddress;
	}

	public String getStaMacAddress() {
		return staMacAddress;
	}

	public void setStationMacAddress(String staMacAddress) {
		this.staMacAddress = staMacAddress;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getMsgType() {
		return msgType;
	}

	public void setMsgType(String msgType) {
		this.msgType = msgType;
	}

}
