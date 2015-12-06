/**
 * Copyright 2015- the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 			http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.machairodus.topology.quartz;

import java.text.ParseException;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import org.machairodus.topology.scan.ComponentScan;
import org.machairodus.topology.util.Assert;
import org.machairodus.topology.util.CollectionUtils;
import org.machairodus.topology.util.RuntimeUtil;
import org.machairodus.topology.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 任务工厂
 * @author yanghe
 * @date 2015年6月8日 下午5:24:13 
 */
public class QuartzFactory {
	private static Logger LOG = LoggerFactory.getLogger(QuartzFactory.class);
	private static QuartzFactory FACTORY;
	private static final Object LOCK = new Object();
	private AtomicInteger quartzSize = new AtomicInteger(0);
	private final ConcurrentMap<String , BaseQuartz> quartzs = new ConcurrentHashMap<String , BaseQuartz>();
	private final ConcurrentMap<String , BaseQuartz> _tmpQuartz = new ConcurrentHashMap<String , BaseQuartz>();
	private final ConcurrentMap<String, Set<BaseQuartz>> group = new ConcurrentHashMap<String, Set<BaseQuartz>>();
	private static final QuartzThreadFactory threadFactory = new QuartzThreadFactory();
	private static final ThreadPoolExecutor service = (ThreadPoolExecutor) Executors.newCachedThreadPool(threadFactory);
	
	private static boolean isLoaded = false;
	
	public static final String BASE_PACKAGE = "context.quartz-scan.base-package";
	public static final String AUTO_RUN = "context.quartz.run.auto";
	public static final String MAX_POINTER = "context.statistic.max.pointer";
	
	private QuartzFactory() {
		
	}
	
	public static final QuartzFactory getInstance() {
		if(FACTORY == null) {
			synchronized (LOCK) {
				if(FACTORY == null)
					FACTORY = new QuartzFactory();
				
			}
		}
		
		return FACTORY;
		
	}
	
	/**
	 * 绑定任务
	 * 
	 * @param quartz 任务
	 * @return 返回当前任务
	 */
	public BaseQuartz bind(BaseQuartz quartz) {
		try {
			quartz.setClose(false);
			quartzs.put(quartz.getConfig().getId(), quartz);
			quartzSize.incrementAndGet();
			
			return quartz;
			
		} finally {
			if(LOG.isInfoEnabled())
				LOG.info("绑定任务: 任务号[ " + quartz.getConfig().getId() + " ]");
			
		}
	}
	
	/**
	 * 解绑任务
	 * 
	 * @param quartz 任务
	 * @return 返回当前任务
	 */
	public BaseQuartz unbind(BaseQuartz quartz) {
		try {
			quartzs.remove(quartz.getConfig().getId());
			quartzSize.decrementAndGet();
			
			return quartz;
			
		} finally {
			if(LOG.isDebugEnabled())
				LOG.debug("解绑任务 : 任务号[ " + quartz.getConfig().getId() + " ], 现存任务数: " + quartzSize.get());
			
		}
	}
	
	/**
	 * 获取现在正在执行的任务数
	 * @return 任务数
	 */
	public int getQuartzSize() {
		return quartzSize.get();
		
	}
	
	/**
	 * 返回所有任务
	 * @return 任务集合
	 */
	public Collection<BaseQuartz> getQuartzs() {
		return quartzs.values();
	}
	
	public int getStopedQuartzSize() {
		return _tmpQuartz.size();
	}
	
	public Collection<BaseQuartz> getStopedQuratz() {
		return _tmpQuartz.values();
	}
	
	/**
	 * 关闭任务
	 * @param id 任务号
	 */
	public void close(String id) {
		try {
			BaseQuartz quartz = quartzs.get(id);
			if(quartz != null && !quartz.isClose()) {
				quartz.setClose(true);
				_tmpQuartz.put(quartz.getConfig().getId(), quartz);
				quartzs.remove(id, quartz);
			}
			
		} finally {
			if(LOG.isDebugEnabled())
				LOG.debug("关闭任务: 任务号[ " + id + " ]");
			
		}
	}
	
	/**
	 * 关闭整组任务
	 * @param groupName
	 */
	public void closeGroup(String groupName) {
		Assert.hasLength(groupName, "groupName must not be null");
		Set<String> ids = new HashSet<String>();
		for(BaseQuartz quartz : quartzs.values()) {
			if(groupName.equals(quartz.getConfig().getGroup())) {
				if(!quartz.isClose()) {
					quartz.setClose(true);
					_tmpQuartz.put(quartz.getConfig().getId(), quartz);
					ids.add(quartz.getConfig().getId());
				}
			}
		}
		
		for(String id : ids) quartzs.remove(id); 
	}
	
	/**
	 * 关闭所有任务
	 */
	public void closeAll() {
		if(quartzs.size() > 0) {
			LOG.warn("现在关闭所有的任务");
			Set<String> ids = new HashSet<String>();
			for(String id : quartzs.keySet()) {
				try {
					BaseQuartz quartz = quartzs.get(id);
					if(quartz != null && !quartz.isClose()) {
						quartz.setClose(true);
						_tmpQuartz.put(quartz.getConfig().getId(), quartz);
						ids.add(quartz.getConfig().getId());
					}
					
				} finally {
					if(LOG.isDebugEnabled())
						LOG.debug("关闭任务: 任务号[ " + id + " ]");
				}
			}
			
			while(getQuartzSize() > 0) try { Thread.sleep(100L); } catch(InterruptedException e) { }
			quartzs.clear();
		}
	}
	
	/**
	 * 启动所有缓冲区中的任务并清理任务缓冲区
	 */
	public final void startAll() {
		if(_tmpQuartz.size() > 0) {
			for(Entry<String, BaseQuartz> entry : _tmpQuartz.entrySet()) {
				String name = entry.getKey();
				BaseQuartz quartz = entry.getValue();
				if(LOG.isInfoEnabled())
					LOG.info("Start quartz [ " + name + " ], class with [ " + quartz.getClass().getName() + " ]");
				
				getInstance().bind(quartz);
				threadFactory.setBaseQuartz(quartz);
				service.execute(quartz);
			}
			
			_tmpQuartz.clear();
		}
	}
	
	public final void startGroup(String groupName) {
		if(_tmpQuartz.size() > 0) {
			Set<String> keys = new HashSet<String>();
			for(Entry<String, BaseQuartz> entry : _tmpQuartz.entrySet()) {
				String id = entry.getKey();
				BaseQuartz quartz = entry.getValue();
				if(groupName.equals(quartz.getConfig().getGroup())) {
					if(quartz.isClose()) {
						if(LOG.isInfoEnabled())
							LOG.info("Start quartz [ " + id + " ], class with [ " + quartz.getClass().getName() + " ]");
						
						getInstance().bind(quartz);
						threadFactory.setBaseQuartz(quartz);
						service.execute(quartz);
						keys.add(id);
					}
				}
			}
			
			for(String key : keys) {
				_tmpQuartz.remove(key);
			}
		}
	}
	
	public final void start(String id) {
		BaseQuartz quartz = _tmpQuartz.get(id);
		if(quartz != null && quartz.isClose()) {
			if(LOG.isInfoEnabled())
				LOG.info("Start quartz [ " + id + " ], class with [ " + quartz.getClass().getName() + " ]");
			
			getInstance().bind(quartz);
			threadFactory.setBaseQuartz(quartz);
			service.execute(quartz);
			_tmpQuartz.remove(id);
		}
	}
	
	public final boolean closed(String id) {
		return _tmpQuartz.containsKey(id);
	}
	
	public final boolean started(String id) {
		return quartzs.containsKey(id);
	}
	
	public final boolean hasClosedGroup(String group) {
		if(_tmpQuartz.size() > 0) {
			for(BaseQuartz quartz : _tmpQuartz.values()) {
				if(quartz.getConfig().getGroup().equals(group))
					return true;
			}
		}
		
		return false;
	}
	
	public final boolean hasStartedGroup(String group) {
		if(quartzs.size() > 0) {
			for(BaseQuartz quartz : quartzs.values()) {
				if(quartz.getConfig().getGroup().equals(group))
					return true;
			}
		}
		
		return false;
	}
	
	public final void addQuartz(BaseQuartz quartz) {
		Set<BaseQuartz> groupQuartz = group.get(quartz.getConfig().getGroup());
		if(groupQuartz == null) groupQuartz = new LinkedHashSet<BaseQuartz>();
		groupQuartz.add(quartz);
		group.put(quartz.getConfig().getGroup(), groupQuartz);
		
		if(_tmpQuartz.containsKey(quartz.getConfig().getId()) || quartzs.containsKey(quartz.getConfig().getId()))
			throw new QuartzException("exists quartz in memory");
		
		_tmpQuartz.put(quartz.getConfig().getId(), quartz);
		rebalance(quartz.getConfig().getGroup());
	}
	
	public final void removeQuartz(BaseQuartz quartz) {
		Set<BaseQuartz> groupQuartz = group.get(quartz.getConfig().getGroup());
		getInstance().close(quartz.getConfig().getId());
		
		if(groupQuartz.size() > 1) {
			groupQuartz.remove(quartz);
			_tmpQuartz.remove(quartz.getConfig().getId(), quartz);
		}
		
		rebalance(quartz.getConfig().getGroup());
	}
	
	public final void removeQuartz(String groupName) {
		BaseQuartz quartz = findLast(groupName);
		if(quartz != null) {
			removeQuartz(quartz);
		}
	}
	
	public final int getGroupSize(String groupName) {
		Set<BaseQuartz> groupQuartz = group.get(groupName);
		if(!CollectionUtils.isEmpty(groupQuartz))
			return groupQuartz.size();
		
		return 0;
	}
	
	public final BaseQuartz findLast(String groupName) {
		Assert.hasLength(groupName);
		Set<BaseQuartz> groupQuartz = group.get(groupName);
		if(!CollectionUtils.isEmpty(groupQuartz)) {
			for(BaseQuartz quartz : groupQuartz) {
				if(quartz.getConfig().getNum() + 1 == quartz.getConfig().getTotal())
					return quartz;
			}
		}
		
		return null;
	}
	
	public final void rebalance(String groupName) {
		Assert.hasLength(groupName);
		Set<BaseQuartz> groupQuartz = group.get(groupName);
		if(!CollectionUtils.isEmpty(groupQuartz)) {
			for(BaseQuartz quartz : groupQuartz) quartz.getConfig().setTotal(groupQuartz.size());
		}
	}
	
	/**
	 * 加载任务调度
	 * @param injector Guice Injector
	 * @throws IllegalArgumentException 非法的参数列表
	 * @throws IllegalAccessException ?
	 */
	public static final void load(Properties properties) throws IllegalArgumentException, IllegalAccessException {
		if(isLoaded) 
			throw new QuartzException("Quartz已经加载，这里不再进行重复的加载，如需重新加载请调用reload方法");

		String _package = properties.getProperty(BASE_PACKAGE);
		if(_package == null || _package.isEmpty())
			throw new QuartzException("Property '" + BASE_PACKAGE + "' must not be null.");
		
		String[] packages = _package.split(",");
		for(String pkg : packages) {
			if(!pkg.isEmpty()) {
				ComponentScan.scan(pkg);
			}
		}
		
		Set<Class<?>> componentClasses = ComponentScan.filter(Quartz.class);
		if(LOG.isInfoEnabled())
			LOG.info("Quartz size: " + componentClasses.size());
		
		if(componentClasses.size() > 0) {
			for(Class<?> clz : componentClasses) {
				if(BaseQuartz.class.isAssignableFrom(clz)) {
					if(LOG.isInfoEnabled())
						LOG.info("Inject Quartz Class: " + clz.getName());
					
					Quartz quartz = clz.getAnnotation(Quartz.class);
					if(quartz.name() == null && quartz.name().isEmpty()) 
						throw new QuartzException("任务名不能为空, 类名 [ " + clz.getName()+ " ]");
					
					String parallelProperty = quartz.parallelProperty();
					int parallel = 0;
					String cron = "";
					String value;
					if((value = properties.getProperty(parallelProperty)) != null && !value.isEmpty()) {
						/** 采用最后设置的属性作为最终结果 */
						try {
							parallel = Integer.parseInt(value);
						} catch(NumberFormatException e) { 
							throw new QuartzException("并行度属性设置错误, 属性名: [ " + parallelProperty + " ], 属性值: [ " + value + " ]");
						}
					}
					
					if((value = properties.getProperty(quartz.cronProperty())) != null && !value.isEmpty())
						cron = value;
					
					parallel = quartz.coreParallel() ? RuntimeUtil.AVAILABLE_PROCESSORS : parallel > 0 ? parallel : quartz.parallel();
					if(parallel < 0)
						parallel = 0;
					
					if(StringUtils.isEmpty(cron))
						cron = quartz.cron();
					
					try {
						for(int p = 0; p < parallel; p ++) {
							BaseQuartz baseQuartz = (BaseQuartz) clz.newInstance();
							QuartzConfig config = new QuartzConfig();
							config.setId(quartz.name() + "-" + p);
							config.setName("Quartz-Thread-Pool: " + quartz.name() + "-" + p);
							config.setGroup(quartz.name());
							config.setService(service);
							config.setBeforeAfterOnly(quartz.beforeAfterOnly());
							config.setRunNumberOfTimes(quartz.runNumberOfTimes());
							config.setInterval(quartz.interval());
							config.setNum(p);
							config.setTotal(parallel);
							if(!StringUtils.isEmpty(cron))
								try { config.setCron(new CronExpression(cron)); } catch(ParseException e) { throw new QuartzException(e.getMessage(), e); }
						
							config.setDaemon(quartz.daemon());
							baseQuartz.setConfig(config);
							
							if(getInstance()._tmpQuartz.containsKey(quartz.name() + "-" + p)) {
								throw new QuartzException("\n\t任务调度重复: " + quartz.name() + "-" + p + ", 组件类: {'" + clz.getName() + "', '" + getInstance()._tmpQuartz.get(quartz.name() + "-" + p).getClass().getName() +"'}");
							}
							
							getInstance()._tmpQuartz.put(config.getId(), baseQuartz);
							
							Set<BaseQuartz> groupQuartz = getInstance().group.get(baseQuartz.getConfig().getGroup());
							if(groupQuartz == null) groupQuartz = new LinkedHashSet<BaseQuartz>();
							groupQuartz.add(baseQuartz);
							getInstance().group.put(config.getGroup(), groupQuartz);
						}
					} catch(Exception e) {
						throw new QuartzException("创建调度任务异常: " + e.getMessage());
					}
				} else 
					throw new QuartzException("必须继承: [ "+BaseQuartz.class.getName()+" ]");
				
			}
			
		}
		
		isLoaded = true;
	}
	
	/**
	 * 重新加载调度任务
	 * @param injector Guice Injector
	 */
	public static final void reload(final Properties properties) {
		getInstance()._tmpQuartz.clear();
		getInstance().closeAll();
		service.execute(new Runnable() {
			
			@Override
			public void run() {
				try { while(QuartzFactory.getInstance().getQuartzSize() > 0) Thread.sleep(100L); } catch(InterruptedException e) { }
				if(LOG.isInfoEnabled())
					LOG.info("所有任务已经全部关闭");
				
				try {
					load(properties);
				} catch (Exception e) {
					LOG.error(e.getMessage(), e);
				}
				
			}
		});
		
	}
}
