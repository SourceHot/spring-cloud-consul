/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.consul.serviceregistry;

import java.util.List;

import com.ecwid.consul.ConsulException;
import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.agent.model.NewService;
import com.ecwid.consul.v1.health.HealthChecksForServiceRequest;
import com.ecwid.consul.v1.health.model.Check;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.cloud.client.serviceregistry.ServiceRegistry;
import org.springframework.cloud.consul.discovery.ConsulDiscoveryProperties;
import org.springframework.cloud.consul.discovery.HeartbeatProperties;
import org.springframework.cloud.consul.discovery.TtlScheduler;
import org.springframework.util.ReflectionUtils;

import static org.springframework.boot.actuate.health.Status.OUT_OF_SERVICE;
import static org.springframework.boot.actuate.health.Status.UP;

/**
 * @author Spencer Gibb
 */
public class ConsulServiceRegistry implements ServiceRegistry<ConsulRegistration> {

	private static Log log = LogFactory.getLog(ConsulServiceRegistry.class);
	/**
	 * consul客户端
	 */
	private final ConsulClient client;
	/**
	 * consul服务注册发现配置
	 */
	private final ConsulDiscoveryProperties properties;
	/**
	 * 定时器
	 */
	private final TtlScheduler ttlScheduler;
	/**
	 * 与心跳验证相关的属性
	 */
	private final HeartbeatProperties heartbeatProperties;

	public ConsulServiceRegistry(ConsulClient client, ConsulDiscoveryProperties properties, TtlScheduler ttlScheduler,
			HeartbeatProperties heartbeatProperties) {
		this.client = client;
		this.properties = properties;
		this.ttlScheduler = ttlScheduler;
		this.heartbeatProperties = heartbeatProperties;
	}

	@Override
	public void register(ConsulRegistration reg) {
		log.info("Registering service with consul: " + reg.getService());
		try {
			// 通过consul客户端进行注册
			this.client.agentServiceRegister(reg.getService(), this.properties.getAclToken());
			// 从请求对象中获取服务
			NewService service = reg.getService();
			// 确认心跳检查是否开启
			// 确认定时器是否存在
			// 确认检查对象是否非空
			// 确认检查对象中的ttl数据是否非空
			if (this.heartbeatProperties.isEnabled() && this.ttlScheduler != null && service.getCheck() != null
				&& service.getCheck().getTtl() != null) {
				// 定时器添加数据
				this.ttlScheduler.add(reg.getService());
			}
		}
		catch (ConsulException e) {
			if (this.properties.isFailFast()) {
				log.error("Error registering service with consul: " + reg.getService(), e);
				ReflectionUtils.rethrowRuntimeException(e);
			}
			log.warn("Failfast is false. Error registering service with consul: " + reg.getService(), e);
		}
	}

	@Override
	public void deregister(ConsulRegistration reg) {
		// 定时器存在的情况下移除数据
		if (this.ttlScheduler != null) {
			this.ttlScheduler.remove(reg.getInstanceId());
		}
		if (log.isInfoEnabled()) {
			log.info("Deregistering service with consul: " + reg.getInstanceId());
		}
		// 通过客户端进行删除实例操作
		this.client.agentServiceDeregister(reg.getInstanceId(), this.properties.getAclToken());
	}

	@Override
	public void close() {

	}

	@Override
	public void setStatus(ConsulRegistration registration, String status) {
		// 状态和OUT_OF_SERVICE相同
		if (status.equalsIgnoreCase(OUT_OF_SERVICE.getCode())) {
			// 设置状态为true
			this.client.agentServiceSetMaintenance(registration.getInstanceId(), true);
		}
		// 状态和UP相同
		else if (status.equalsIgnoreCase(UP.getCode())) {
			// 设置状态为false
			this.client.agentServiceSetMaintenance(registration.getInstanceId(), false);
		}
		// 其他情况抛出异常
		else {
			throw new IllegalArgumentException("Unknown status: " + status);
		}

	}

	@Override
	public Object getStatus(ConsulRegistration registration) {
		// 获取服务id
		String serviceId = registration.getServiceId();
		// 获取心跳数据
		Response<List<Check>> response = this.client.getHealthChecksForService(serviceId,
			HealthChecksForServiceRequest.newBuilder().setQueryParams(QueryParams.DEFAULT).build());
		// 获取检查对象集合
		List<Check> checks = response.getValue();

		// 循环检查对象
		for (Check check : checks) {
			// 获取检查对象中的服务id和当前的服务id进行比较，如果相同并且名称是Service Maintenance Mode将返回OUT_OF_SERVICE
			if (check.getServiceId().equals(registration.getInstanceId())) {
				if (check.getName().equalsIgnoreCase("Service Maintenance Mode")) {
					return OUT_OF_SERVICE.getCode();
				}
			}
		}

		// 返回UP
		return UP.getCode();
	}

}
