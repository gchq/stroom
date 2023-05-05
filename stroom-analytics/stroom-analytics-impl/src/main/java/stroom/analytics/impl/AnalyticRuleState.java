package stroom.analytics.impl;

public record AnalyticRuleState(Integer id,
                                int version,
                                long createTime,
                                String createUser,
                                long updateTime,
                                String updateUser,
                                String analyticUuid,
                                Long lastMetaId,
                                Long lastEventId,
                                Long lastEventTime,
                                Long lastExecutionTime) {

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Integer id;
        private int version;
        private long createTime;
        private String createUser;
        private long updateTime;
        private String updateUser;
        private String analyticUuid;
        private Long lastMetaId;
        private Long lastEventId;
        private Long lastEventTime;
        private Long lastExecutionTime;

        private Builder() {
        }

        private Builder(final AnalyticRuleState analyticRuleState) {
            this.id = analyticRuleState.id;
            this.version = analyticRuleState.version;
            this.createTime = analyticRuleState.createTime;
            this.createUser = analyticRuleState.createUser;
            this.updateTime = analyticRuleState.updateTime;
            this.updateUser = analyticRuleState.updateUser;
            this.analyticUuid = analyticRuleState.analyticUuid;
            this.lastMetaId = analyticRuleState.lastMetaId;
            this.lastEventId = analyticRuleState.lastEventId;
            this.lastEventTime = analyticRuleState.lastEventTime;
            this.lastExecutionTime = analyticRuleState.lastExecutionTime;
        }

        public Builder id(final Integer id) {
            this.id = id;
            return this;
        }

        public Builder version(final int version) {
            this.version = version;
            return this;
        }

        public Builder createTime(final long createTime) {
            this.createTime = createTime;
            return this;
        }

        public Builder createUser(final String createUser) {
            this.createUser = createUser;
            return this;
        }

        public Builder updateTime(final long updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public Builder updateUser(final String updateUser) {
            this.updateUser = updateUser;
            return this;
        }

        public Builder analyticUuid(final String analyticUuid) {
            this.analyticUuid = analyticUuid;
            return this;
        }

        public Builder lastMetaId(final Long lastMetaId) {
            this.lastMetaId = lastMetaId;
            return this;
        }

        public Builder lastEventId(final Long lastEventId) {
            this.lastEventId = lastEventId;
            return this;
        }

        public Builder lastEventTime(final Long lastEventTime) {
            this.lastEventTime = lastEventTime;
            return this;
        }

        public Builder lastExecutionTime(final Long lastExecutionTime) {
            this.lastExecutionTime = lastExecutionTime;
            return this;
        }

        public AnalyticRuleState build() {
            return new AnalyticRuleState(
                    id,
                    version,
                    createTime,
                    createUser,
                    updateTime,
                    updateUser,
                    analyticUuid,
                    lastMetaId,
                    lastEventId,
                    lastEventTime,
                    lastExecutionTime);
        }
    }
}
