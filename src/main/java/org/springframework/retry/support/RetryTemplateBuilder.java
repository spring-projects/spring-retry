package org.springframework.retry.support;


import java.util.ArrayList;
import java.util.List;

import org.springframework.classify.BinaryExceptionClassifier;
import org.springframework.classify.BinaryExceptionClassifierBuilder;
import org.springframework.retry.RetryListener;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.BackOffPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.backoff.NoBackOffPolicy;
import org.springframework.retry.backoff.UniformRandomBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.policy.BinaryExceptionClassifierRetryPolicy;
import org.springframework.retry.policy.CompositeRetryPolicy;
import org.springframework.retry.policy.MapRetryContextCache;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.policy.RetryContextCache;
import org.springframework.retry.policy.TimeoutRetryPolicy;
import org.springframework.util.Assert;

/**
 * Fluent API to configure new instance of RetryTemplate.
 * All time units are in milliseconds.
 * For detailed description of each builder method - see it's doc.
 *
 * <p>
 * Examples:
 * <pre>{@code
 * RetryTemplate.newBuilder()
 *      .maxAttempts(10)
 *      .exponentialBackoff(100, 2, 10000)
 *      .retryOn(IOException.class)
 *      .traversingCauses()
 *      .build();
 *
 * RetryTemplate.newBuilder()
 *      .fixedBackoff(10)
 *      .withinMillis(3000)
 *      .build();
 *
 * RetryTemplate.newBuilder()
 *      .infiniteRetry()
 *      .retryOn(IOException.class)
 *      .uniformRandomBackoff(1000, 3000)
 *      .build();
 * }</pre>
 *
 * <p>
 * The builder provides the following defaults:
 * <ul>
 * <li> max attempts = 3 (initial + 2 retries) Inherited from {@link MaxAttemptsRetryPolicy} </li>
 * <li> retry only on {@link Exception} and it's subclasses, without traversing causes
 *      Inherited from {@link BinaryExceptionClassifier#getDefaultClassifier()} </li>
 * <li> no backoff (retry immediately) </li>
 * <li> throw an {@link org.springframework.retry.ExhaustedRetryException} on exhausted,
 *      instead of rethrowing original throwable. Inherited from {@link RetryTemplate} </li>
 * <li> {@link MapRetryContextCache} implementation for retryContextCache. Inherited from
 *      {@link RetryTemplate} </li>
 * </ul>
 * <p>
 * Not thread safe. Building should be performed in a single thread. Also, there is no
 * guarantee that all constructors of all fields are thread safe in-depth (means employing
 * only volatile and final writes), so, in concurrent environment, it is recommended to
 * ensure presence of happens-before between publication and any usage. (e.g. publication
 * via volatile write)
 *
 * @author Aleksandr Shamukov
 * @since 1.3
 */
public class RetryTemplateBuilder {

    private RetryPolicy baseRetryPolicy;
    private BackOffPolicy backOffPolicy;
    private List<RetryListener> listeners;
    private BinaryExceptionClassifierBuilder classifierBuilder;
    private RetryContextCache retryContextCache;
    private Boolean throwLastExceptionOnExhausted;

    /* ---------------- Configure retry policy -------------- */

    /**
     * Limits maximum number of attempts to provided value.
     * <p>
     * Invocation of this method does not discard default exception classification rule,
     * that is "retry only on {@link Exception} and it's subclasses".
     *
     * @param maxAttempts includes initial attempt and all retries. E.g: maxAttempts = 3 means
     *                    one initial attempt and two retries.
     * @see MaxAttemptsRetryPolicy
     */
    public RetryTemplateBuilder maxAttempts(int maxAttempts) {
        Assert.isTrue( maxAttempts > 0, "Number of attempts should be positive");
        Assert.isNull(baseRetryPolicy, "You have already selected another retry policy");
        baseRetryPolicy = new MaxAttemptsRetryPolicy(maxAttempts);
        return this;
    }

    /**
     * Allows retry if there is no more than {@code timeout} millis since first attempt.
     * <p>
     * Invocation of this method does not discard default exception classification rule,
     * that is "retry only on {@link Exception} and it's subclasses".
     *
     * @param timeout whole execution timeout in milliseconds
     * @see TimeoutRetryPolicy
     */
    public RetryTemplateBuilder withinMillis(long timeout) {
        Assert.isTrue(timeout > 0, "Timeout should be positive");
        Assert.isNull(baseRetryPolicy, "You have already selected another retry policy");
        TimeoutRetryPolicy timeoutRetryPolicy = new TimeoutRetryPolicy();
        timeoutRetryPolicy.setTimeout(timeout);
        this.baseRetryPolicy = timeoutRetryPolicy;
        return this;
    }

    /**
     * Allows infinite retry, do not limit attempts by number or time.
     * <p>
     * Invocation of this method does not discard default exception classification rule,
     * that is "retry only on {@link Exception} and it's subclasses".
     *
     * @see TimeoutRetryPolicy
     */
    public RetryTemplateBuilder infiniteRetry() {
        Assert.isNull(baseRetryPolicy, "You have already selected another retry policy");
        baseRetryPolicy = new AlwaysRetryPolicy();
        return this;
    }

    /**
     * If flexibility of this builder is not enough for you, you can provide your own
     * {@link RetryPolicy} via this method.
     * <p>
     * Invocation of this method does not discard default exception classification rule,
     * that is "retry only on {@link Exception} and it's subclasses".
     *
     * @param policy will be directly set to resulting {@link RetryTemplate}
     */
    public RetryTemplateBuilder customPolicy(RetryPolicy policy) {
        Assert.notNull(policy, "Policy should not be null");
        Assert.isNull(baseRetryPolicy, "You have already selected another retry policy");
        baseRetryPolicy = policy;
        return this;
    }


    /* ---------------- Configure backoff policy -------------- */

    /**
     * Use exponential backoff policy.
     *
     * @see ExponentialBackOffPolicy
     */
    public RetryTemplateBuilder exponentialBackoff(long initialInterval, double multiplier, long maxInterval) {
        return exponentialBackoff(initialInterval, multiplier, maxInterval, false);
    }

    /**
     * Use exponential backoff policy with optional randomness.
     *
     * @see ExponentialBackOffPolicy
     * @see ExponentialRandomBackOffPolicy
     */
    public RetryTemplateBuilder exponentialBackoff(long initialInterval, double multiplier, long maxInterval,
            boolean withRandom) {
        Assert.isNull(backOffPolicy, "You have already selected backoff policy");
        Assert.isTrue(initialInterval >= 1, "Initial interval should be >= 1");
        Assert.isTrue(multiplier > 1, "Multiplier should be > 1");
        Assert.isTrue(maxInterval > initialInterval,
                "Max interval should be > than initial interval");
        ExponentialBackOffPolicy policy = withRandom
                ? new ExponentialRandomBackOffPolicy()
                : new ExponentialBackOffPolicy();
        policy.setInitialInterval(initialInterval);
        policy.setMultiplier(multiplier);
        policy.setMaxInterval(maxInterval);
        backOffPolicy = policy;
        return this;
    }

    /**
     * Retry after fixed amount of millis.
     *
     * @see FixedBackOffPolicy
     */
    public RetryTemplateBuilder fixedBackoff(long interval) {
        Assert.isNull(backOffPolicy, "You have already selected backoff policy");
        Assert.isTrue(interval >= 1, "Interval should be >= 1");
        FixedBackOffPolicy policy = new FixedBackOffPolicy();
        policy.setBackOffPeriod(interval);
        backOffPolicy = policy;
        return this;
    }

    /**
     * Use {@link UniformRandomBackOffPolicy}, see it's doc for details.
     *
     * @see UniformRandomBackOffPolicy
     */
    public RetryTemplateBuilder uniformRandomBackoff(long minInterval, long maxInterval) {
        Assert.isNull(backOffPolicy, "You have already selected backoff policy");
        Assert.isTrue(minInterval >= 1, "Min interval should be >= 1");
        Assert.isTrue(maxInterval >= 1, "Max interval should be >= 1");
        Assert.isTrue(maxInterval > minInterval,
                "Max interval should be > than min interval");
        UniformRandomBackOffPolicy policy = new UniformRandomBackOffPolicy();
        policy.setMinBackOffPeriod(minInterval);
        policy.setMaxBackOffPeriod(maxInterval);
        backOffPolicy = policy;
        return this;
    }

    /**
     * Do not pause between attempts, retry immediately.
     *
     * @see NoBackOffPolicy
     */
    public RetryTemplateBuilder noBackoff() {
        Assert.isNull(backOffPolicy, "You have already selected backoff policy");
        backOffPolicy = new NoBackOffPolicy();
        return this;
    }

    /**
     * You can provide your own {@link BackOffPolicy} via this method.
     *
     * @param backOffPolicy will be directly set to resulting {@link RetryTemplate}
     */
    public RetryTemplateBuilder customBackoff(BackOffPolicy backOffPolicy) {
        Assert.isNull(this.backOffPolicy, "You have already selected backoff policy");
        Assert.notNull(backOffPolicy, "You should provide non null custom policy");
        this.backOffPolicy = backOffPolicy;
        return this;
    }


    /* ---------------- Configure exception classifier -------------- */

    /**
     * Add a throwable to the while list of retryable exceptions.
     * <p>
     * Warn: touching this method drops default {@code retryOn(Exception.class)}
     * and you should configure whole classifier from scratch.
     * <p>
     * You should select the way you want to configure exception classifier:
     * white list or black list. If you choose white list - use this method,
     * if black - use {@link #notRetryOn(Class)}
     *
     * @param throwable to be retryable (with it's subclasses)
     * @see BinaryExceptionClassifierBuilder#retryOn
     * @see BinaryExceptionClassifier
     */
    public RetryTemplateBuilder retryOn(Class<? extends Throwable> throwable) {
        classifierBuilder().retryOn(throwable);
        return this;
    }


    /**
     * Add a throwable to the black list of retryable exceptions.
     * <p>
     * Warn: touching this method drops default {@code retryOn(Exception.class)}
     * and you should configure whole classifier from scratch.
     * <p>
     * You should select the way you want to configure exception classifier:
     * white list or black list. If you choose black list - use this method,
     * if white - use {@link #retryOn(Class)}
     *
     * @param throwable to be not retryable (with it's subclasses)
     * @see BinaryExceptionClassifierBuilder#notRetryOn
     * @see BinaryExceptionClassifier
     */
    public RetryTemplateBuilder notRetryOn(Class<? extends Throwable> throwable) {
        classifierBuilder().notRetryOn(throwable);
        return this;
    }

    /**
     * Suppose throwing a {@code new MyLogicException(new IOException())}.
     * This template will not retry on it:
     * <pre>{@code
     * RetryTemplate.newBuilder()
     *          .retryOn(IOException.class)
     *          .build()
     * }</pre>
     * but this will retry:
     * <pre>{@code
     * RetryTemplate.newBuilder()
     *          .retryOn(IOException.class)
     *          .traversingCauses()
     *          .build()
     * }</pre>
     *
     * @see BinaryExceptionClassifier
     */
    public RetryTemplateBuilder traversingCauses() {
        classifierBuilder().traversingCauses();
        return this;
    }


    /* ---------------- Add listeners -------------- */

    /**
     * Appends provided {@code listener} to {@link RetryTemplate}'s listener list.
     *
     * @param listener to be appended
     * @see RetryTemplate
     * @see RetryListener
     */
    public RetryTemplateBuilder withListener(RetryListener listener) {
        Assert.notNull(listener, "Listener should not be null");
        listenersList().add(listener);
        return this;
    }

    /**
     * Appends all provided {@code listeners} to {@link RetryTemplate}'s listener list.
     *
     * @param listeners to be appended
     * @see RetryTemplate
     * @see RetryListener
     */
    public RetryTemplateBuilder withListeners(List<RetryListener> listeners) {
        for (final RetryListener listener : listeners) {
            Assert.notNull(listener, "Listener should not be null");
        }
        listenersList().addAll(listeners);
        return this;
    }


    /* ---------------- Other properties -------------- */

    public RetryTemplateBuilder throwLastExceptionOnExhausted() {
        this.throwLastExceptionOnExhausted = true;
        return this;
    }

    public RetryTemplateBuilder retryContextCache(RetryContextCache retryContextCache) {
        Assert.isNull(this.retryContextCache, "You have already selected retry context cache");
        Assert.notNull(retryContextCache, "You should provide non null retry context cache");
        this.retryContextCache = retryContextCache;
        return this;
    }


    /* ---------------- Building -------------- */

    /**
     * Finish configuration and build resulting {@link RetryTemplate}.
     * For default behaviour and concurrency note see class-level doc of {@link RetryTemplateBuilder}.
     *
     * @implNote
     * The {@code retryPolicy} of the returned {@link RetryTemplate} is always
     * an instance of {@link CompositeRetryPolicy}, that consists of one base
     * policy, and of {@link BinaryExceptionClassifierRetryPolicy}.
     * The motivation is: whatever base policy we use, exception classification
     * is extremely recommended.
     *
     * @return new instance of {@link RetryTemplate}
     */
    public RetryTemplate build() {
        RetryTemplate retryTemplate = new RetryTemplate();

        // Exception classifier

        BinaryExceptionClassifier exceptionClassifier = classifierBuilder != null
                ? classifierBuilder.build()
                : BinaryExceptionClassifier.getDefaultClassifier();

        // Retry policy

        if (baseRetryPolicy == null) {
            baseRetryPolicy = new MaxAttemptsRetryPolicy();
        }

        CompositeRetryPolicy finalPolicy = new CompositeRetryPolicy();
        finalPolicy.setPolicies(new RetryPolicy[] {
                baseRetryPolicy,
                new BinaryExceptionClassifierRetryPolicy(exceptionClassifier)
        });
        retryTemplate.setRetryPolicy(finalPolicy);

        // Backoff policy

        if (backOffPolicy == null) {
            backOffPolicy = new NoBackOffPolicy();
        }
        retryTemplate.setBackOffPolicy(backOffPolicy);

        // Listeners

        if (listeners != null) {
            retryTemplate.setListeners(listeners.toArray(new RetryListener[0]));
        }

        // Others

        if (throwLastExceptionOnExhausted != null) {
            retryTemplate.setThrowLastExceptionOnExhausted(throwLastExceptionOnExhausted);
        }
        if (retryContextCache != null) {
            retryTemplate.setRetryContextCache(retryContextCache);
        }

        return retryTemplate;
    }


    /* ---------------- Private utils -------------- */

    private BinaryExceptionClassifierBuilder classifierBuilder() {
        if (classifierBuilder == null) {
            classifierBuilder = new BinaryExceptionClassifierBuilder();
        }
        return classifierBuilder;
    }

    private List<RetryListener> listenersList() {
        if (listeners == null) {
            listeners = new ArrayList<RetryListener>();
        }
        return listeners;
    }
}
