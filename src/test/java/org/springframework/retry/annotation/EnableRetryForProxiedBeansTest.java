package org.springframework.retry.annotation;

import org.junit.Test;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.Assert.assertEquals;

/**
 * @author Sparkbit.pl
 */
public class EnableRetryForProxiedBeansTest {

    @Test
    public void proxiedService() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                ProxiedTestConfiguration.class);

        IService service = context.getBean("proxied", IService.class);
        service.service();
        assertEquals(3, service.getCount());

        context.close();
    }

    @Configuration
    @EnableRetry
    protected static class ProxiedTestConfiguration {
        @Bean(name = "realService")
        public IService realService() {
            return new ServiceWithInterface();
        }

        @Bean(name = "proxied")
        public IService service(@Qualifier("realService") IService service) {
            ProxyFactoryBean pfb = new ProxyFactoryBean();
            pfb.setTarget(service);
            pfb.setInterfaces(IService.class);
            IService proxied = (IService) pfb.getObject();
            return proxied;
        }
    }

    protected static interface IService {
        void service();

        int getCount();
    }

    protected static class ServiceWithInterface implements IService {

        private int count = 0;

        @Retryable(RuntimeException.class)
        public void service() {
            if (count++ < 2) {
                throw new RuntimeException("Planned");
            }
        }

        public int getCount() {
            return count;
        }

    }
}
