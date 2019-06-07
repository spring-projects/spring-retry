/*
 * Copyright 2015-2019 the original author or authors.
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
package org.springframework.retry.policy;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.retry.RetryContext;
import org.springframework.util.Assert;

/**
 * Subclass of {@link SimpleRetryPolicy} that delegates to super.canRetry() and, if true,
 * further evaluates an expression against the last thrown exception.
 *
 * @author Gary Russell
 * @author Aldo Sinanaj
 * @since 1.2
 *
 */
@SuppressWarnings("serial")
public class ExpressionRetryPolicy extends SimpleRetryPolicy implements BeanFactoryAware {

	private static final Log logger = LogFactory.getLog(ExpressionRetryPolicy.class);

	private static final TemplateParserContext PARSER_CONTEXT = new TemplateParserContext();

	private final Expression expression;

	private final StandardEvaluationContext evaluationContext = new StandardEvaluationContext();

	/**
	 * Construct an instance with the provided {@link Expression}.
	 * @param expression the expression
	 */
	public ExpressionRetryPolicy(Expression expression) {
		Assert.notNull(expression, "'expression' cannot be null");
		this.expression = expression;
	}

	/**
	 * Construct an instance with the provided expression.
	 * @param expressionString the expression.
	 */
	public ExpressionRetryPolicy(String expressionString) {
		Assert.notNull(expressionString, "'expressionString' cannot be null");
		this.expression = getExpression(expressionString);
	}

	/**
	 * Construct an instance with the provided {@link Expression}.
	 * @param maxAttempts the max attempts
	 * @param retryableExceptions the exceptions
	 * @param traverseCauses true to examine causes
	 * @param expression the expression
	 */
	public ExpressionRetryPolicy(int maxAttempts, Map<Class<? extends Throwable>, Boolean> retryableExceptions,
			boolean traverseCauses, Expression expression) {
		super(maxAttempts, retryableExceptions, traverseCauses);
		Assert.notNull(expression, "'expression' cannot be null");
		this.expression = expression;
	}

	/**
	 * Construct an instance with the provided expression.
	 * @param maxAttempts the max attempts
	 * @param retryableExceptions the exceptions
	 * @param traverseCauses true to examine causes
	 * @param expressionString the expression.
	 * @param defaultValue the default action
	 */
	public ExpressionRetryPolicy(int maxAttempts, Map<Class<? extends Throwable>, Boolean> retryableExceptions,
			boolean traverseCauses, String expressionString, boolean defaultValue) {
		super(maxAttempts, retryableExceptions, traverseCauses, defaultValue);
		Assert.notNull(expressionString, "'expressionString' cannot be null");
		this.expression = getExpression(expressionString);
	}

	@Override
	public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
		this.evaluationContext.setBeanResolver(new BeanFactoryResolver(beanFactory));
	}

	public ExpressionRetryPolicy withBeanFactory(BeanFactory beanFactory) {
		setBeanFactory(beanFactory);
		return this;
	}

	@Override
	public boolean canRetry(RetryContext context) {
		Throwable lastThrowable = context.getLastThrowable();
		if (lastThrowable == null) {
			return super.canRetry(context);
		}
		else {
			return super.canRetry(context)
					&& this.expression.getValue(this.evaluationContext, lastThrowable, Boolean.class);
		}
	}

	/**
	 * Get expression based on the expression string. At the moment supports both literal
	 * and template expressions. Template expressions are deprecated.
	 * @param expression the expression string
	 * @return literal expression or template expression
	 */
	private static Expression getExpression(String expression) {
		if (isTemplate(expression)) {
			logger.warn("#{...} syntax is not required for this run-time expression "
					+ "and is deprecated in favor of a simple expression string");
			return new SpelExpressionParser().parseExpression(expression, PARSER_CONTEXT);
		}
		return new SpelExpressionParser().parseExpression(expression);
	}

	/**
	 * Check if the expression is a template
	 * @param expression the expression string
	 * @return true if the expression string is a template
	 */
	private static boolean isTemplate(String expression) {
		return expression.contains(PARSER_CONTEXT.getExpressionPrefix())
				&& expression.contains(PARSER_CONTEXT.getExpressionSuffix());
	}

}
