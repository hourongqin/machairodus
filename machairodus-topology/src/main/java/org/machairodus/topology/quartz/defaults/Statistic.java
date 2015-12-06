package org.machairodus.topology.quartz.defaults;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

public class Statistic {

	private static Logger logger = Logger.getLogger(Statistic.class);
	private static ConcurrentMap<String , AtomicLong> statisticMap = new ConcurrentHashMap<String , AtomicLong>();
	private static AtomicInteger timePoint = new AtomicInteger(0);
	private static int MAX_POINTER = -1;
	private static LinkedBlockingQueue<List<Map<String , Object>>> pointerQueue = new LinkedBlockingQueue<List<Map<String , Object>>>();
	public static final String TOTAL = "total";
	private static boolean isSetPointer = false;
	private static ReentrantLock LOCK = new ReentrantLock();
	
	public static synchronized void setMaxPointer(int maxPointer) {
		if(!isSetPointer) {
			MAX_POINTER = maxPointer;
			pointerQueue.clear();
			pointerQueue = new LinkedBlockingQueue<List<Map<String , Object>>>(maxPointer);
			isSetPointer = true;
		} else 
			throw new IllegalStateException("Can not reset maxPointer again. You can restart application to reset maxPointer.");
	}
	
	public static long incrementAndGet(String scene) {
		final ReentrantLock lock = LOCK;
		try {
			lock.lock();
			AtomicLong _val = statisticMap.get(scene);
			AtomicLong total = statisticMap.get(TOTAL);
			if(_val != null)
				_val.incrementAndGet();
			else {
				_val = new AtomicLong(0L);
				_val.incrementAndGet();
			}
			
			if(total == null)
				total = new AtomicLong(0L);
			
			total.incrementAndGet();
			
			statisticMap.put(scene, _val);
			statisticMap.put(TOTAL, total);
			
			return _val.get();
		} finally {
			lock.unlock();
		}
	}
	
	public static long get(String scene) {
		AtomicLong _val = statisticMap.get(scene);
		if(_val == null) {
			_val = new AtomicLong(0L);
			statisticMap.put(scene, _val);
		}
			
		return _val.get();
	}
	
	public static void setPointer(int time) {
		final ReentrantLock lock = LOCK;
		try {
			lock.lock();
			long xpoint = timePoint.incrementAndGet() * time;
			List<Map<String , Object>> pointer = new ArrayList<Map<String , Object>>();
			for(Entry<String, AtomicLong> item : statisticMap.entrySet()) {
				Map<String , Object> xy = new HashMap<String , Object>();
				xy.put("scene", item.getKey());
				xy.put("second", xpoint);
				xy.put("tps", item.getValue().get());
				pointer.add(xy);
			}
			
			if(!pointerQueue.offer(pointer)) {
				try { pointerQueue.poll(10 , TimeUnit.MILLISECONDS); } catch (Exception e) { logger.error(e.getMessage() , e); }
				pointerQueue.offer(pointer);
				
			}
			
			for(AtomicLong value : statisticMap.values()) {
				value.set(0L);
			}
			
			if(MAX_POINTER > -1 && timePoint.get() >= MAX_POINTER)
				timePoint.set(0);
			
		} finally {
			lock.unlock();
		}
	}
	
	public static LinkedBlockingQueue<List<Map<String , Object>>> getPointerQueue() {
		return pointerQueue;
	}
	
}
