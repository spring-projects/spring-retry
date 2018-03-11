This project provides declarative retry support for Spring
applications. It is used in Spring Batch, Spring Integration,
Spring for Apache Hadoop (amongst others).

## Quick Start

Example:

```java
@Configuration
@EnableRetry
public class Application {

    @Bean
    public Service service() {
        return new Service();
    }

}

@Service
class Service {
    @Retryable(RemoteAccessException.class)
    public void service() {
        // ... do something
    }
    @Recover
    public void recover(RemoteAccessException e) {
       // ... panic
    }
}
```

Call the "service" method and if it fails with a `RemoteAccessException` then it will retry (up to three times by default), and then execute the "recover" method if unsuccessful. There are various options in the `@Retryable` annotation attributes for including and excluding exception types, limiting the number of retries and the policy for backoff.

## Building

Requires Java 1.7 and Maven 3.0.5 (or greater)

```
$ mvn install
```

## Features and API

### RetryTemplate

To make processing more robust and less prone to failure, sometimes it helps to automatically retry a failed operation in case it might succeed on a subsequent attempt. Errors that are susceptible to this kind of treatment are transient in nature. For example a remote call to a web service or RMI service that fails because of a network glitch or a `DeadLockLoserException` in a database update may resolve themselves after a short wait. To automate the retry of such operations Spring Retry has the `RetryOperations` strategy. The `RetryOperations` interface looks like this:

```java
public interface RetryOperations {

    <T> T execute(RetryCallback<T> retryCallback) throws Exception;

    <T> T execute(RetryCallback<T> retryCallback, RecoveryCallback<T> recoveryCallback)
        throws Exception;

    <T> T execute(RetryCallback<T> retryCallback, RetryState retryState)
        throws Exception, ExhaustedRetryException;

    <T> T execute(RetryCallback<T> retryCallback, RecoveryCallback<T> recoveryCallback,
        RetryState retryState) throws Exception;

}
```

The basic callback is a simple interface that allows you to insert some business logic to be retried:

```java
public interface RetryCallback<T> {

    T doWithRetry(RetryContext context) throws Throwable;

}
```

The callback is executed and if it fails (by throwing an `Exception`), it will be retried until either it is successful, or the implementation decides to abort. There are a number of overloaded execute methods in the `RetryOperations` interface dealing with various use cases for recovery when all retry attempts are exhausted, and also with retry state, which allows clients and implementations to store information between calls (more on this later).

The simplest general purpose implementation of `RetryOperations` is `RetryTemplate`. It could be used like this

```java
RetryTemplate template = new RetryTemplate();

TimeoutRetryPolicy policy = new TimeoutRetryPolicy();
policy.setTimeout(30000L);

template.setRetryPolicy(policy);

Foo result = template.execute(new RetryCallback<Foo>() {

    public Foo doWithRetry(RetryContext context) {
        // Do stuff that might fail, e.g. webservice operation
        return result;
    }

});
```

In the example we execute a web service call and return the result to the user. If that call fails then it is retried until a timeout is reached.

### RetryContext

The method parameter for the `RetryCallback` is a `RetryContext`. Many callbacks will simply ignore the context, but if necessary it can be used as an attribute bag to store data for the duration of the iteration.

A `RetryContext` will have a parent context if there is a nested retry in progress in the same thread. The parent context is occasionally useful for storing data that need to be shared between calls to execute.

### RecoveryCallback

When a retry is exhausted the `RetryOperations` can pass control to a different callback, the `RecoveryCallback`. To use this feature clients just pass in the callbacks together to the same method, for example:

```
Foo foo = template.execute(new RetryCallback<Foo>() {
    public Foo doWithRetry(RetryContext context) {
        // business logic here
    },
  new RecoveryCallback<Foo>() {
    Foo recover(RetryContext context) throws Exception {
          // recover logic here
    }
});
```

If the business logic does not succeed before the template decides to abort, then the client is given the chance to do some alternate processing through the recovery callback.

## Stateless Retry

In the simplest case, a retry is just a while loop: the `RetryTemplate` can just keep trying until it either succeeds or fails. The `RetryContext` contains some state to determine whether to retry or abort, but this state is on the stack and there is no need to store it anywhere globally, so we call this stateless retry. The distinction between stateless and stateful retry is contained in the implementation of the `RetryPolicy` (the `RetryTemplate` can handle both). In a stateless retry, the callback is always executed in the same thread on retry as when it failed.

## Stateful Retry

Where the failure has caused a transactional resource to become invalid, there are some special considerations. This does not apply to a simple remote call because there is no transactional resource (usually), but it does sometimes apply to a database update, especially when using Hibernate. In this case it only makes sense to rethrow the exception that called the failure immediately so that the transaction can roll back and we can start a new valid one.

In these cases a stateless retry is not good enough because the re-throw and roll back necessarily involve leaving the `RetryOperations.execute()` method and potentially losing the context that was on the stack. To avoid losing it we have to introduce a storage strategy to lift it off the stack and put it (at a minimum) in heap storage. For this purpose Spring Retry provides a storage strategy `RetryContextCache` which can be injected into the `RetryTemplate`. The default implementation of the `RetryContextCache` is in memory, using a simple `Map`. It has a strictly enforced maximum capacity, to avoid memory leaks, but it doesn't have any advanced cache features like time to live. You should consider injecting a `Map` that had those features if you need them. Advanced usage with multiple processes in a clustered environment might also consider implementing the `RetryContextCache` with a cluster cache of some sort (though, even in a clustered environment this might be overkill).

Part of the responsibility of the `RetryOperations` is to recognize the failed operations when they come back in a new execution (and usually wrapped in a new transaction). To facilitate this, Spring Retry provides the `RetryState` abstraction. This works in conjunction with a special execute methods in the `RetryOperations`.

The way the failed operations are recognized is by identifying the state across multiple invocations of the retry. To identify the state, the user can provide an `RetryState` object that is responsible for returning a unique key identifying the item. The identifier is used as a key in the `RetryContextCache`.

> *Warning:*
Be very careful with the implementation of `Object.equals()` and `Object.hashCode()` in the key returned by `RetryState`. The best advice is to use a business key to identify the items. In the case of a JMS message the message ID can be used.

When the retry is exhausted there is also the option to handle the failed item in a different way, instead of calling the `RetryCallback` (which is presumed now to be likely to fail). Just like in the stateless case, this option is provided by the `RecoveryCallback`, which can be provided by passing it in to the execute method of `RetryOperations`.

The decision to retry or not is actually delegated to a regular `RetryPolicy`, so the usual concerns about limits and timeouts can be injected there (see below).

## Retry Policies

Inside a `RetryTemplate` the decision to retry or fail in the execute method is determined by a `RetryPolicy` which is also a factory for the `RetryContext`. The `RetryTemplate` has the responsibility to use the current policy to create a `RetryContext` and pass that in to the `RetryCallback` at every attempt. After a callback fails the `RetryTemplate` has to make a call to the `RetryPolicy` to ask it to update its state (which will be stored in the `RetryContext`), and then it asks the policy if another attempt can be made. If another attempt cannot be made (e.g. a limit is reached or a timeout is detected) then the policy is also responsible for identifying the exhausted state, but not for handling the exception. The `RetryTemplate` will throw the original exception, except in the stateful case, when no recover is available, in which case it throws `RetryExhaustedException`. You can also set a flag in the `RetryTemplate` to have it unconditionally throw the original exception from the callback (i.e. from user code) instead.

> *Tip:*
Failures are inherently either retryable or not - if the same exception is always going to be thrown from the business logic, it doesn't help to retry it. So don't retry on all exception types - try to focus on only those exceptions that you expect to be retryable. It's not usually harmful to the business logic to retry more aggressively, but it's wasteful because if a failure is deterministic there will be time spent retrying something that you know in advance is fatal.

Spring Retry provides some simple general purpose implementations of stateless RetryPolicy, for example a `SimpleRetryPolicy`, and the `TimeoutRetryPolicy` used in the example above.

The `SimpleRetryPolicy` just allows a retry on any of a named list of exception types, up to a fixed number of times:

```java
// Set the max attempts including the initial attempt before retrying
// and retry on all exceptions (this is the default):
SimpleRetryPolicy policy = new SimpleRetryPolicy(5, Collections.singletonMap(Exception.class, true));

// Use the policy...
RetryTemplate template = new RetryTemplate();
template.setRetryPolicy(policy);
template.execute(new RetryCallback<Foo>() {
    public Foo doWithRetry(RetryContext context) {
        // business logic here
    }
});
```

There is also a more flexible implementation called `ExceptionClassifierRetryPolicy`, which allows the user to configure different retry behavior for an arbitrary set of exception types though the `ExceptionClassifier` abstraction. The policy works by calling on the classifier to convert an exception into a delegate RetryPolicy, so for example, one exception type can be retried more times before failure than another by mapping it to a different policy.

Users might need to implement their own retry policies for more customized decisions. For instance, if there is a well-known, solution-specific, classification of exceptions into retryable and not retryable.

## Backoff Policies

When retrying after a transient failure it often helps to wait a bit before trying again, because usually the failure is caused by some problem that will only be resolved by waiting. If a `RetryCallback` fails, the `RetryTemplate` can pause execution according to the `BackoffPolicy` in place.

```java
public interface BackoffPolicy {

    BackOffContext start(RetryContext context);

    void backOff(BackOffContext backOffContext)
        throws BackOffInterruptedException;

}
```

A `BackoffPolicy` is free to implement the backOff in any way it chooses. The policies provided by Spring Retry out of the box all use `Object.wait()`. A common use case is to backoff with an exponentially increasing wait period, to avoid two retries getting into lock step and both failing - this is a lesson learned from the ethernet. For this purpose Spring Retry provides the `ExponentialBackoffPolicy`. There are also randomized versions delay policies that are quite useful to avoid resonating between related failures in a complex system.

## Listeners

Often it is useful to be able to receive additional callbacks for cross cutting concerns across a number of different retries. For this purpose Spring Retry provides the `RetryListener` interface. The `RetryTemplate` allows users to register RetryListeners, and they will be given callbacks with the `RetryContext` and `Throwable` where available during the iteration.

The interface looks like this:

```java
public interface RetryListener {

    void open(RetryContext context, RetryCallback<T> callback);

    void onError(RetryContext context, RetryCallback<T> callback, Throwable e);

    void close(RetryContext context, RetryCallback<T> callback, Throwable e);
}
```

The open and close callbacks come before and after the entire retry in the simplest case and `onError` applies to the individual `RetryCallback` calls. The close method might also receive a `Throwable`; if there has been an error it is the last one thrown by the `RetryCallback`.

Note that when there is more than one listener, they are in a list, so there is an order. In this case open will be called in the same order while onError and close will be called in reverse order.

## Declarative Retry

Sometimes there is some business processing that you know you want to retry every time it happens. The classic example of this is the remote service call. Spring Retry provides an AOP interceptor that wraps a method call in a `RetryOperations` for just this purpose. The `RetryOperationsInterceptor` executes the intercepted method and retries on failure according to the `RetryPolicy` in the provided `RepeatTemplate`.

### Java Configuration for Retry Proxies

Add the `@EnableRetry` annotation to one of your `@Configuration` classes and use `@Retryable` on the methods (or type level for all methods) that you want to retry. You can also specify any number of retry listeners. Example

```java
@Configuration
@EnableRetry
public class Application {

    @Bean
    public Service service() {
        return new Service();
    }
    
    @Bean public RetryListener retryListener1() {
        return new RetryListener() {...}
    }
    
    @Bean public RetryListener retryListener2() {
        return new RetryListener() {...}
    }

}

@Service
class Service {
    @Retryable(RemoteAccessException.class)
    public service() {
        // ... do something
    }
}
```

Attributes of `@Retryable` can be used to control the `RetryPolicy` and `BackoffPolicy`, e.g.

```java
@Service
class Service {
    @Retryable(maxAttempts=12, backoff=@Backoff(delay=100, maxDelay=500))
    public service() {
        // ... do something
    }
}
```

for a random backoff between 100 and 500 milliseconds and up to 12 attempts. There is also a `stateful` attribute (default false) to control whether the retry is stateful or not. To use stateful retry the intercepted method has to have arguments, since they are used to construct the cache key for the state.

The `@EnableRetry` annotation also looks for beans of type `Sleeper` and other strategies used in the `RetryTemplate` and interceptors to control the beviour of the retry at runtime.

The `@EnableRetry` annotation creates proxies for `@Retryable` beans, and the proxies (so the bean instances in the application) have the `Retryable` interface added to them. This is purely a marker interface, but might be useful for other tools looking to apply retry advice (they should usually not bother if the bean already implements `Retryable`).

Recovery method can be supplied, in case you want to take an alternative code path when the retry is exhausted. Methods should be declared in the same class as the `@Retryable` and marked `@Recover`. The return type must match the `@Retryable` method. The arguments for the recovery method can optionally include the exception that was thrown, and also optionally the arguments passed to the orginal retryable method (or a partial list of them as long as none are omitted). Example:

```java
@Service
class Service {
    @Retryable(RemoteAccessException.class)
    public void service(String str1, String str2) {
        // ... do something
    }
    @Recover
    public void recover(RemoteAccessException e, String str1, String str2) {
       // ... error handling making use of original args if required
    }
}
```

Version 1.2 introduces the ability to use expressions for certain properties:

```java

@Retryable(exceptionExpression="#{message.contains('this can be retried')}")
public void service1() {
  ...
}

@Retryable(exceptionExpression="#{message.contains('this can be retried')}")
public void service2() {
  ...
}

@Retryable(exceptionExpression="#{@exceptionChecker.shouldRetry(#root)}",
    maxAttemptsExpression = "#{@integerFiveBean}",
  backoff = @Backoff(delayExpression = "#{1}", maxDelayExpression = "#{5}", multiplierExpression = "#{1.1}"))
public void service3() {
  ...
}
```

These use the familier Spring SpEL expression syntax (`#{...}`).

Expressions can contain property placeholders such as `#{${max.delay}}` or `#{@exceptionChecker.${retry.method}(#root)}`

- `exceptionExpression` is evaluated against the thrown exception as the `#root` object.
- `maxAttemptsExpression` and the `@BackOff` expression attributes are evaluated once, during initialization; there is no root object for the evaluation but they can reference other beans in the context.


### XML Configuration

Here is an example of declarative iteration using Spring AOP to repeat a service call to a method called `remoteCall` (for more detail on how to configure AOP interceptors see the Spring User Guide):

```xml
<aop:config>
    <aop:pointcut id="transactional"
        expression="execution(* com..*Service.remoteCall(..))" />
    <aop:advisor pointcut-ref="transactional"
        advice-ref="retryAdvice" order="-1"/>
</aop:config>

<bean id="retryAdvice"
    class="org.springframework.retry.interceptor.RetryOperationsInterceptor"/>
```

The example above uses a default `RetryTemplate` inside the interceptor. To change the policies or listeners, you only need to inject an instance of `RetryTemplate` into the interceptor.

## Contributing

Spring Retry is released under the non-restrictive Apache 2.0 license,
and follows a very standard Github development process, using Github
tracker for issues and merging pull requests into master. If you want
to contribute even something trivial please do not hesitate, but
follow the guidelines below.

Before we accept a non-trivial patch or pull request we will need you
to sign the https://cla.pivotal.io/[contributor's agreement].  Signing
the contributor's agreement does not grant anyone commit rights to the
main repository, but it does mean that we can accept your
contributions, and you will get an author credit if we do.  Active
contributors might be asked to join the core team, and given the
ability to merge pull requests.

## Code of Conduct
This project adheres to the [Contributor Covenant](https://github.com/spring-projects/spring-retry/blob/master/CODE_OF_CONDUCT.adoc).
By participating, you  are expected to uphold this code. Please report unacceptable behavior to
spring-code-of-conduct@pivotal.io.
