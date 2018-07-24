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

import cn.fi.obj.sub.EventSubscription;
import cn.fi.obj.sub.NotificationCallback;

/**
 * 所有应用的父类，定义了为应用提供的接口函数
 * 
 */
public abstract class Application implements Runnable {

	private ApplicationInterface applicationInterface;// 为应用提供调用接口的具体实现

	public abstract void run();

	public void setApplicationInterface(ApplicationInterface applicationInterface) {
		this.applicationInterface = applicationInterface;
	}

	public Station getStation(String staMacAddress) {
		return applicationInterface.getStation(staMacAddress);
	}

	public Agent getAgent(String AgentMacAddress) {
		return applicationInterface.getAgent(AgentMacAddress);
	}

	/* 非强制漫游分配一个Vap调用函数 */
	protected void addVapContainer(boolean isNoForceRoam, Agent agent, String staMacAddress, String mode, String ssid) {
		applicationInterface.addVapContainer(isNoForceRoam, agent, staMacAddress, mode, ssid);
	}

	/* 发送漫游切换命令 */
	protected void handoffVap(Station station, Agent fromAgent, Agent toAgent, String ssid) {
		applicationInterface.handoffVap(station, fromAgent, toAgent, ssid);
	}

	/* 删除一个Agent */
	protected void delVapContainer(String roamType, Agent agent, Station station, String ssid) {
		applicationInterface.delVapContainer(roamType, agent, station, ssid);
	}

	/* 注册一个订阅 */
	protected final String registerSubscription(String appName, EventSubscription oes, NotificationCallback cb) {
		return applicationInterface.registerSubscription(appName, oes, cb);
	}

	/* 取消一个订阅 */
	protected final void unRegisterSubscription(String id, String MsgType) {
		applicationInterface.unRegisterSubscription(MsgType, id);
	}

}
