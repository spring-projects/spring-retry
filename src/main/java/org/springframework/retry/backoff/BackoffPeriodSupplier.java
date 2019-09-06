package org.springframework.retry.backoff;

import java.util.function.Supplier;

public interface BackoffPeriodSupplier extends Supplier<Long> {

}
