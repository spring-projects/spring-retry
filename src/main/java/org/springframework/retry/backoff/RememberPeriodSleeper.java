package org.springframework.retry.backoff;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class RememberPeriodSleeper implements Sleeper, LastBackoffPeriodSupplier {

    private static final Log logger = LogFactory.getLog(RememberPeriodSleeper.class);

    private volatile Long lastBackoffPeriod;

    @Override
    public void sleep(long backOffPeriod) {
        logger.debug("Remembering a sleeping period instead of sleeping: " + backOffPeriod);
        lastBackoffPeriod = backOffPeriod;
    }

    @Override
    public Long get() {
        return lastBackoffPeriod;
    }
}
