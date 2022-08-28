package stroom.proxy.repo;

public class ForwardAggregate {

    private final long id;
    private final long updateTimeMs;
    private final Aggregate aggregate;
    private final ForwardDest forwardDest;
    private final boolean success;
    private final String error;
    private final long tries;

    public ForwardAggregate(final long id,
                            final long updateTimeMs,
                            final Aggregate aggregate,
                            final ForwardDest forwardDest,
                            final boolean success,
                            final String error,
                            final long tries) {
        this.id = id;
        this.updateTimeMs = updateTimeMs;
        this.aggregate = aggregate;
        this.forwardDest = forwardDest;
        this.success = success;
        this.error = error;
        this.tries = tries;
    }

    public long getId() {
        return id;
    }

    public long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public Aggregate getAggregate() {
        return aggregate;
    }

    public ForwardDest getForwardDest() {
        return forwardDest;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getError() {
        return error;
    }

    public long getTries() {
        return tries;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static class Builder {

        private long id;
        private long updateTimeMs;
        private Aggregate aggregate;
        private ForwardDest forwardDest;
        private boolean success;
        private String error;
        private long tries;

        public Builder() {
        }

        public Builder(final ForwardAggregate forwardAggregate) {
            this.id = forwardAggregate.id;
            this.updateTimeMs = forwardAggregate.updateTimeMs;
            this.aggregate = forwardAggregate.aggregate;
            this.forwardDest = forwardAggregate.forwardDest;
            this.success = forwardAggregate.success;
            this.error = forwardAggregate.error;
            this.tries = forwardAggregate.tries;
        }

        public Builder id(final long id) {
            this.id = id;
            return this;
        }

        public Builder updateTimeMs(final long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return this;
        }

        public Builder aggregate(final Aggregate aggregate) {
            this.aggregate = aggregate;
            return this;
        }

        public Builder forwardDest(final ForwardDest forwardDest) {
            this.forwardDest = forwardDest;
            return this;
        }

        public Builder success(final boolean success) {
            this.success = success;
            return this;
        }

        public Builder error(final String error) {
            this.error = error;
            return this;
        }

        public Builder tries(final long tries) {
            this.tries = tries;
            return this;
        }

        public ForwardAggregate build() {
            return new ForwardAggregate(id, updateTimeMs, aggregate, forwardDest, success, error, tries);
        }
    }
}
