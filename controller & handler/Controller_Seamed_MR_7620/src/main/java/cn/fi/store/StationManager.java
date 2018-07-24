/*   
 * Copyright (c) 2010-2020 Founder Ltd. All Rights Reserved.   
 *   
 * This software is the confidential and proprietary information of   
 * Founder. You shall not disclose such Confidential Information   
 * and shall use it only in accordance with the terms of the agreements   
 * you entered into with Founder.   
 *   
 */
package cn.fi.store;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.fi.main.Master;
import cn.fi.obj.Redis;
import cn.fi.obj.Station;

/**
 * Station的管理视图，存储station的基本信息
 * 
 */

public class StationManager {
	// map，以staion的mac地址为索引
	private final Map<String, Station> stationMap = new ConcurrentHashMap<String, Station>();

	// 存储到视图
	public void addStation(final Station station) {
		stationMap.put(station.getMacAddress(), station);
		if (Master.REDIS_USED) {
			Redis.addStation(station.getMacAddress(), Master.OFF_LINE);
		}
	}

	// 判断视图是否包含
	public boolean isTracked(String staMacAddr) {
		return stationMap.containsKey(staMacAddr);
	}

	// 从视图中删除
	public void removeStation(final String staMacAddr) {
		stationMap.remove(staMacAddr);
		if (Master.REDIS_USED) {
			Redis.delStation(staMacAddr);
		}
	}

	// get方法
	public Station getStation(final String staMacAddr) {
		return stationMap.get(staMacAddr);
	}

	public Map<String, Station> getStations() {
		return Collections.unmodifiableMap(stationMap);
	}
}
