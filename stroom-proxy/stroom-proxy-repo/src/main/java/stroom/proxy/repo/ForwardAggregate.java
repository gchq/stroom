package stroom.proxy.repo;

public class ForwardAggregate {

    private final long id;
    private final long updateTimeMs;
    private final Aggregate aggregate;
    private final ForwardUrl forwardUrl;
    private final boolean success;
    private final String error;
    private final long tries;

    public ForwardAggregate(final long id,
                            final long updateTimeMs,
                            final Aggregate aggregate,
                            final ForwardUrl forwardUrl,
                            final boolean success,
                            final String error,
                            final long tries) {
        this.id = id;
        this.updateTimeMs = updateTimeMs;
        this.aggregate = aggregate;
        this.forwardUrl = forwardUrl;
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

    public ForwardUrl getForwardUrl() {
        return forwardUrl;
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
        private ForwardUrl forwardUrl;
        private boolean success;
        private String error;
        private long tries;

        public Builder() {
        }

        public Builder(final ForwardAggregate forwardAggregate) {
            this.id = forwardAggregate.id;
            this.updateTimeMs = forwardAggregate.updateTimeMs;
            this.aggregate = forwardAggregate.aggregate;
            this.forwardUrl = forwardAggregate.forwardUrl;
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

        public Builder forwardUrl(final ForwardUrl forwardUrl) {
            this.forwardUrl = forwardUrl;
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
            return new ForwardAggregate(id, updateTimeMs, aggregate, forwardUrl, success, error, tries);
        }
    }
}
