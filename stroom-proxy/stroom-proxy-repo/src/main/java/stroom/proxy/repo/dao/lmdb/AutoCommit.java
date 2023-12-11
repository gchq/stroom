package stroom.proxy.repo.dao.lmdb;

import java.time.Duration;
import java.time.Instant;

public class AutoCommit {

    private final long maxItems;
    private final Duration maxTime;
    private Instant lastCommit = Instant.now();
    private long count;

    public AutoCommit(final long maxItems, final Duration maxTime) {
        this.maxItems = maxItems;
        this.maxTime = maxTime;
    }

    public boolean shouldCommit() {
        count++;
        if (count > maxItems) {
            count = 0;
            lastCommit = Instant.now();
            return true;
        } else {
            final Instant now = Instant.now();
            if (now.minus(maxTime).isAfter(lastCommit)) {
                count = 0;
                lastCommit = now;
                return true;
            }
        }
        return false;
    }
}
