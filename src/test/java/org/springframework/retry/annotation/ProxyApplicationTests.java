package org.springframework.retry.annotation;

import static org.junit.Assert.assertEquals;

import java.net.URLClassLoader;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Vector;

import org.junit.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.test.util.ReflectionTestUtils;

public class ProxyApplicationTests {

	private Set<Class<?>> classes = new HashSet<Class<?>>();

	@Test
	// See gh-53
	public void contextLoads() throws Exception {
		int count = count();
		runAndClose();
		runAndClose();
		// Let the JVM catch up
		Thread.sleep(500L);
		runAndClose();
		int base = count();
		runAndClose();
		count = count();
		assertEquals("Class leak", base, count);
		runAndClose();
		count = count();
		assertEquals("Class leak", base, count);
		runAndClose();
		count = count();
		assertEquals("Class leak", base, count);
	}

	@SuppressWarnings("resource")
	private void runAndClose() {
		ConfigurableApplicationContext run = new AnnotationConfigApplicationContext(
				Empty.class);
		run.close();
		while (run.getParent() != null) {
			((ConfigurableApplicationContext) run.getParent()).close();
			run = (ConfigurableApplicationContext) run.getParent();
		}
	}

	private int count() {
		URLClassLoader classLoader = (URLClassLoader) getClass().getClassLoader();
		@SuppressWarnings("unchecked")
		Vector<Class<?>> classes = (Vector<Class<?>>) ReflectionTestUtils
				.getField(classLoader, "classes");
		Set<Class<?>> news = new HashSet<Class<?>>();
		for (Iterator<Class<?>> iterator = classes.iterator(); iterator.hasNext();) {
			Class<?> cls = (Class<?>) iterator.next();
			if (!this.classes.contains(cls)) {
				news.add(cls);
			}
		}
		this.classes.addAll(classes);
		return classes.size();
	}

	@Configuration
	@EnableRetry(proxyTargetClass = true)
	protected static class Empty {

		@Bean
		public Service service() {
			return new Service();
		}

	}

	@Component
	static class Service {

		@Retryable
		public void handle() {
			System.err.println("Handling");
		}

	}

}
