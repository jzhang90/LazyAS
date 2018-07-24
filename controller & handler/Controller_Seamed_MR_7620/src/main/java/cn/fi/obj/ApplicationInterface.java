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
 * 需要实现的一些操作接口，提供给应用使用
 * 
 */
public interface ApplicationInterface {
	public String registerSubscription(String appName, final EventSubscription es, final NotificationCallback cb);

	public void unRegisterSubscription(String msgType, String subscriptionId);

	public Agent getAgent(String agentMacAddr);

	public Station getStation(String staMacAddress);

	public void addVapContainer(boolean isNoForceRoam, Agent agent, String staMacAddress, String mode, String ssid);

	void delVapContainer(String roamType, Agent agent, Station station, String ssid);

	void handoffVap(Station station, Agent fromAgent, Agent toAgent, String ssid);	
}
