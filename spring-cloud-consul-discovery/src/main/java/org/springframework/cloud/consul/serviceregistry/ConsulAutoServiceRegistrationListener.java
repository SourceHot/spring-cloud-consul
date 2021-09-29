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

import org.springframework.boot.web.context.ConfigurableWebServerApplicationContext;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;

/**
 * Auto registers service upon web server initialization.
 *
 * @author Spencer Gibb
 */
public class ConsulAutoServiceRegistrationListener implements SmartApplicationListener {

	private final ConsulAutoServiceRegistration autoServiceRegistration;

	public ConsulAutoServiceRegistrationListener(ConsulAutoServiceRegistration autoServiceRegistration) {
		this.autoServiceRegistration = autoServiceRegistration;
	}

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return WebServerInitializedEvent.class.isAssignableFrom(eventType);
	}

	@Override
	public boolean supportsSourceType(Class<?> sourceType) {
		return true;
	}

	@Override
	public void onApplicationEvent(ApplicationEvent applicationEvent) {
		// 确认事件类型是否是WebServerInitializedEvent
		if (applicationEvent instanceof WebServerInitializedEvent) {
			// 类型转换
			WebServerInitializedEvent event = (WebServerInitializedEvent) applicationEvent;
			// 获取应用上下文
			ApplicationContext context = event.getApplicationContext();
			// 如果应用上下文类型是ConfigurableWebServerApplicationContext，并且命名空间是management则跳过处理
			if (context instanceof ConfigurableWebServerApplicationContext) {
				if ("management".equals(((ConfigurableWebServerApplicationContext) context).getServerNamespace())) {
					return;
				}
			}
			// 设置端口
			this.autoServiceRegistration.setPortIfNeeded(event.getWebServer().getPort());
			// 启动
			this.autoServiceRegistration.start();
		}
	}

	@Override
	public int getOrder() {
		return 0;
	}

}
