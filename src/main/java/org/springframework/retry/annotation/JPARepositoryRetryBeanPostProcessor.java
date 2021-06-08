/*
 * Copyright 2021 the original author or authors.
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

package org.springframework.retry.annotation;

import org.springframework.aop.Advisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.retry.interceptor.Retryable;

/**
 * Add a bean of this instance if you wish to annotate JPA Repository interfaces with
 * {@code @Retryable}. It merges the repository proxy advisors with the retry advisor.
 *
 * @author Gary Russell
 * @since 1.3.2
 */
public class JPARepositoryRetryBeanPostProcessor implements BeanPostProcessor, Ordered {

	@Override
	public int getOrder() {
		return Integer.MAX_VALUE;
	}

	@Override
	public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
		if (bean instanceof Retryable && bean instanceof Advised) {
			Advised advised = (Advised) bean;
			try {
				Object target = advised.getTargetSource().getTarget();
				if (target instanceof Advised) {
					Advised advised2 = (Advised) target;
					Object target2 = advised2.getTargetSource().getTarget();
					if (target2 != null) {
						ProxyFactory pf = new ProxyFactory(target2);
						pf.removeInterface(JpaRepositoryImplementation.class);
						for (Advisor advisor : advised.getAdvisors()) {
							pf.addAdvisor(advisor);
						}
						for (Advisor advisor : advised2.getAdvisors()) {
							pf.addAdvisor(advisor);
						}
						for (Class<?> iface : advised2.getProxiedInterfaces()) {
							pf.addInterface(iface);
						}
						return pf.getProxy();
					}
				}
			}
			catch (Exception e) {
				e.printStackTrace();
			}
		}
		return bean;
	}

	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
		return bean;
	}

}
