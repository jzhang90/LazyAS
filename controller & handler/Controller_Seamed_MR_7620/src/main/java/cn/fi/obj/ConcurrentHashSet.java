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

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/*
 * 基于ConcurrentHashmap实现的可并发操作的集合,传统的set不支持并发
 * 
 * */

public class ConcurrentHashSet<E> {
	private ConcurrentHashMap<E, Object> map;
	private static final Object PRESENT = new Object();

	// 默认构造函数
	public ConcurrentHashSet() {
		map = new ConcurrentHashMap<E, Object>();
	}

	public ConcurrentHashSet(int initialCapacity, float loadFactor) {
		map = new ConcurrentHashMap<E, Object>(initialCapacity, loadFactor);
	}

	public ConcurrentHashSet(int initialCapacity) {
		map = new ConcurrentHashMap<E, Object>(initialCapacity);
	}

	/*
	 * 返回HashSet的迭代器
	 */
	public Iterator<E> iterator() {
		return map.keySet().iterator();
	}

	public Set<E> getValues() {
		return map.keySet();
	}

	public int size() {
		return map.size();
	}

	public boolean isEmpty() {
		return map.isEmpty();
	}

	public boolean contains(Object o) {
		return map.containsKey(o);
	}

	/* 将元素(e)添加到HashSet中 */
	public boolean add(E e) {
		return map.put(e, PRESENT) == null;
	}

	/* 删除HashSet中的元素(o) */
	public boolean remove(Object o) {
		return map.remove(o) == PRESENT;
	}

	public void clear() {
		map.clear();
	}

	@Override
	public String toString() {
		return "ConcurrentHashSet [map=" + map.toString() + "]";
	}

}
