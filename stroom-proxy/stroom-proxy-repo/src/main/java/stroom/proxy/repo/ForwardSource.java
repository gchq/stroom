package stroom.proxy.repo;

public class ForwardSource {

    private final long id;
    private final long updateTimeMs;
    private final RepoSource source;
    private final ForwardDest forwardDest;
    private final boolean success;
    private final String error;
    private final long tries;

    public ForwardSource(final long id,
                         final long updateTimeMs,
                         final RepoSource source,
                         final ForwardDest forwardDest,
                         final boolean success,
                         final String error,
                         final long tries) {
        this.id = id;
        this.updateTimeMs = updateTimeMs;
        this.source = source;
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

    public RepoSource getSource() {
        return source;
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
        private RepoSource source;
        private ForwardDest forwarddest;
        private boolean success;
        private String error;
        private long tries;

        public Builder() {
        }

        public Builder(final ForwardSource forwardSource) {
            this.id = forwardSource.id;
            this.updateTimeMs = forwardSource.updateTimeMs;
            this.source = forwardSource.source;
            this.forwarddest = forwardSource.forwardDest;
            this.success = forwardSource.success;
            this.error = forwardSource.error;
            this.tries = forwardSource.tries;
        }

        public Builder id(final long id) {
            this.id = id;
            return this;
        }

        public Builder updateTimeMs(final long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return this;
        }

        public Builder source(final RepoSource source) {
            this.source = source;
            return this;
        }

        public Builder forwardDest(final ForwardDest forwarddest) {
            this.forwarddest = forwarddest;
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

        public ForwardSource build() {
            return new ForwardSource(id, updateTimeMs, source, forwarddest, success, error, tries);
        }
    }
}
