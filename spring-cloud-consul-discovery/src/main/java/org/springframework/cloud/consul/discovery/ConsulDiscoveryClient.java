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

package org.springframework.cloud.consul.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.ecwid.consul.v1.ConsulClient;
import com.ecwid.consul.v1.QueryParams;
import com.ecwid.consul.v1.Response;
import com.ecwid.consul.v1.catalog.CatalogServicesRequest;
import com.ecwid.consul.v1.health.HealthServicesRequest;
import com.ecwid.consul.v1.health.model.HealthService;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;

/**
 * @author Spencer Gibb
 * @author Joe Athman
 * @author Tim Ysewyn
 * @author Chris Bono
 */
public class ConsulDiscoveryClient implements DiscoveryClient {

	/**
	 * consul客户端
	 */
	private final ConsulClient client;

	/**
	 * consul服务发现和注册的配置
	 */
	private final ConsulDiscoveryProperties properties;

	public ConsulDiscoveryClient(ConsulClient client, ConsulDiscoveryProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@Override
	public String description() {
		// 描述
		return "Spring Cloud Consul Discovery Client";
	}

	@Override
	public List<ServiceInstance> getInstances(final String serviceId) {
		// 通过getInstances方法获取服务实例集合
		return getInstances(serviceId, new QueryParams(this.properties.getConsistencyMode()));
	}

	public List<ServiceInstance> getInstances(final String serviceId, final QueryParams queryParams) {
		// 创建实例集合
		List<ServiceInstance> instances = new ArrayList<>();

		// 搜索并且向实例集合中加入数据
		addInstancesToList(instances, serviceId, queryParams);

		// 返回实例集合
		return instances;
	}


	private void addInstancesToList(List<ServiceInstance> instances, String serviceId, QueryParams queryParams) {
		// 创建请求构造器对象
		HealthServicesRequest.Builder requestBuilder = HealthServicesRequest.newBuilder()
			.setPassing(properties.isQueryPassing()).setQueryParams(queryParams).setToken(properties.getAclToken());
		// consul服务发现和注册的配置中获取服务id的标签集合
		String[] queryTags = properties.getQueryTagsForService(serviceId);
		// 标签集合不为空的情况下向查询构造器设置标签集合
		if (queryTags != null) {
			requestBuilder.setTags(queryTags);
		}
		// 构建请求对象
		HealthServicesRequest request = requestBuilder.build();

		// 发送请求
		Response<List<HealthService>> services = this.client.getHealthServices(serviceId, request);

		// 处理响应结果将其放入到服务实例对象集合中
		for (HealthService service : services.getValue()) {
			instances.add(new ConsulServiceInstance(service, serviceId));
		}
	}

	public List<ServiceInstance> getAllInstances() {
		List<ServiceInstance> instances = new ArrayList<>();

		Response<Map<String, List<String>>> services = this.client
				.getCatalogServices(CatalogServicesRequest.newBuilder().setQueryParams(QueryParams.DEFAULT).build());
		for (String serviceId : services.getValue().keySet()) {
			addInstancesToList(instances, serviceId, QueryParams.DEFAULT);
		}
		return instances;
	}

	@Override
	public List<String> getServices() {
		// 创建请求对象
		CatalogServicesRequest request = CatalogServicesRequest.newBuilder().setQueryParams(QueryParams.DEFAULT)
			.setToken(this.properties.getAclToken()).build();
		// 请求后将请求 结果返回
		return new ArrayList<>(this.client.getCatalogServices(request).getValue().keySet());
	}

	@Override
	public void probe() {
		this.client.getStatusLeader();
	}

	@Override
	public int getOrder() {
		return this.properties.getOrder();
	}

}
