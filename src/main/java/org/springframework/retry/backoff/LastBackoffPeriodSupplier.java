package org.springframework.retry.backoff;

import java.util.function.Supplier;

public interface LastBackoffPeriodSupplier extends Supplier<Long> {
}
