/**
 * Copyright 2015-2016 the original author or authors.
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
package org.machairodus.balancer.service.impl;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import org.machairodus.balancer.quartz.JmxMonitorQuartz;
import org.machairodus.balancer.service.CommandService;
import org.machairodus.commons.util.ResponseStatus;
import org.machairodus.mappers.domain.NodeConfig;
import org.nanoframework.commons.util.CollectionUtils;
import org.nanoframework.core.status.ResultMap;
import org.nanoframework.extension.concurrent.quartz.BaseQuartz;
import org.nanoframework.extension.concurrent.quartz.QuartzConfig;
import org.nanoframework.extension.concurrent.quartz.QuartzFactory;
import org.nanoframework.extension.concurrent.quartz.QuartzThreadFactory;

public class CommandServiceImpl implements CommandService {
	private static final AtomicLong PARALLEL = new AtomicLong(0);
	private static final QuartzThreadFactory threadFactory = new QuartzThreadFactory();
	private static final ThreadPoolExecutor service = (ThreadPoolExecutor) Executors.newCachedThreadPool(threadFactory);
	private ReentrantLock LOCK = new ReentrantLock();
	private QuartzFactory FACTORY = QuartzFactory.getInstance();
	
	@Override
	public ResultMap createScheduler(NodeConfig nodeConfig) {
		ReentrantLock lock = LOCK;
		try {
			lock.lock();
			QuartzConfig config = new QuartzConfig();
			Long parallel;
			config.setId(JmxMonitorQuartz.class.getSimpleName() + "-" + (parallel = PARALLEL.getAndIncrement()));
			config.setName("Quartz-Thread-Pool: " + config.getId());
			config.setGroup(JmxMonitorQuartz.class.getSimpleName());
			config.setService(service);
			config.setBeforeAfterOnly(true);
			config.setNum(parallel.intValue());
			config.setTotal(PARALLEL.intValue());
			config.setDaemon(true);
			config.setLazy(true);
			JmxMonitorQuartz quartz = new JmxMonitorQuartz(config, nodeConfig);
			FACTORY.addQuartz(quartz);
			FACTORY.start(config.getId());
			
			return ResponseStatus.OK;
		} catch(Throwable e) {
			PARALLEL.decrementAndGet();
			return ResultMap.create(ResponseStatus.FAIL.getStatus(), "createScheduler: " + e.getMessage(), ResponseStatus.FAIL.getInfo());
			
		} finally {
			lock.unlock();
		}
	}

	@Override
	public ResultMap destroyScheduler(Long nodeId) {
		ReentrantLock lock = LOCK;
		try {
			lock.lock();
			Set<BaseQuartz> quartzs = FACTORY.getGroupQuartz(JmxMonitorQuartz.class.getSimpleName());
			if(!CollectionUtils.isEmpty(quartzs)) {
				quartzs.stream().filter(quartz -> ((JmxMonitorQuartz) quartz).getNodeConfigId().equals(nodeId)).forEach(quartz -> {
					FACTORY.removeQuartz(quartz, true);
					PARALLEL.decrementAndGet();
				});
			}
			
			return ResponseStatus.OK;
		} catch(Throwable e) {
			return ResultMap.create(ResponseStatus.FAIL.getStatus(), "destroyScheduler: " + e.getMessage(), ResponseStatus.FAIL.getInfo());
			
		} finally {
			lock.unlock();
		}
	}
}
