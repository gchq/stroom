package stroom.analytics.shared;

import stroom.query.api.v2.ExpressionOperator;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticProcessorFilter {

    @JsonProperty
    private final String uuid;
    @JsonProperty
    private final int version;
    @JsonProperty
    private final Long createTimeMs;
    @JsonProperty
    private final Long updateTimeMs;
    @JsonProperty
    private final String createUser;
    @JsonProperty
    private final String updateUser;
    @JsonProperty
    private final String analyticUuid;
    @JsonProperty
    private final boolean enabled;
    @JsonProperty
    private final ExpressionOperator expression;
    @JsonProperty
    private final Long minMetaCreateTimeMs;
    @JsonProperty
    private final Long maxMetaCreateTimeMs;
    @JsonProperty
    private final String node;

    @JsonCreator
    public AnalyticProcessorFilter(@JsonProperty("uuid") final String uuid,
                                   @JsonProperty("version") final int version,
                                   @JsonProperty("createTimeMs") final Long createTimeMs,
                                   @JsonProperty("updateTimeMs") final Long updateTimeMs,
                                   @JsonProperty("createUser") final String createUser,
                                   @JsonProperty("updateUser") final String updateUser,
                                   @JsonProperty("analyticUuid") final String analyticUuid,
                                   @JsonProperty("enabled") final boolean enabled,
                                   @JsonProperty("expression") final ExpressionOperator expression,
                                   @JsonProperty("minMetaCreateTimeMs") final Long minMetaCreateTimeMs,
                                   @JsonProperty("maxMetaCreateTimeMs") final Long maxMetaCreateTimeMs,
                                   @JsonProperty("node") final String node) {
        this.uuid = uuid;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.updateTimeMs = updateTimeMs;
        this.createUser = createUser;
        this.updateUser = updateUser;
        this.analyticUuid = analyticUuid;
        this.enabled = enabled;
        this.expression = expression;
        this.minMetaCreateTimeMs = minMetaCreateTimeMs;
        this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
        this.node = node;
    }

    public String getUuid() {
        return uuid;
    }

    public int getVersion() {
        return version;
    }

    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public String getCreateUser() {
        return createUser;
    }

    public String getUpdateUser() {
        return updateUser;
    }


    public String getAnalyticUuid() {
        return analyticUuid;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public Long getMinMetaCreateTimeMs() {
        return minMetaCreateTimeMs;
    }

    public Long getMaxMetaCreateTimeMs() {
        return maxMetaCreateTimeMs;
    }

    public String getNode() {
        return node;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticProcessorFilter filter = (AnalyticProcessorFilter) o;
        return enabled == filter.enabled &&
                Objects.equals(uuid, filter.uuid) &&
                Objects.equals(version, filter.version) &&
                Objects.equals(createTimeMs, filter.createTimeMs) &&
                Objects.equals(updateTimeMs, filter.updateTimeMs) &&
                Objects.equals(createUser, filter.createUser) &&
                Objects.equals(updateUser, filter.updateUser) &&
                Objects.equals(analyticUuid, filter.analyticUuid) &&
                Objects.equals(expression, filter.expression) &&
                Objects.equals(minMetaCreateTimeMs, filter.minMetaCreateTimeMs) &&
                Objects.equals(maxMetaCreateTimeMs, filter.maxMetaCreateTimeMs) &&
                Objects.equals(node, filter.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid,
                version,
                createTimeMs,
                updateTimeMs,
                createUser,
                updateUser,
                analyticUuid,
                enabled,
                expression,
                minMetaCreateTimeMs,
                maxMetaCreateTimeMs,
                node);
    }

    @Override
    public String toString() {
        return "AnalyticProcessorFilter{" +
                "uuid='" + uuid + '\'' +
                ", version='" + version + '\'' +
                ", createTimeMs=" + createTimeMs +
                ", updateTimeMs=" + updateTimeMs +
                ", createUser='" + createUser + '\'' +
                ", updateUser='" + updateUser + '\'' +
                ", analyticUuid='" + analyticUuid + '\'' +
                ", enabled=" + enabled +
                ", expression=" + expression +
                ", minMetaCreateTimeMs=" + minMetaCreateTimeMs +
                ", maxMetaCreateTimeMs=" + maxMetaCreateTimeMs +
                ", node='" + node + '\'' +
                '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private String uuid;
        private int version;
        private Long createTimeMs;
        private Long updateTimeMs;
        private String createUser;
        private String updateUser;
        private String analyticUuid;
        private boolean enabled;
        private ExpressionOperator expression;
        private Long minMetaCreateTimeMs;
        private Long maxMetaCreateTimeMs;
        private String node;

        private Builder() {
        }

        private Builder(final AnalyticProcessorFilter filter) {
            this.uuid = filter.uuid;
            this.version = filter.version;
            this.createTimeMs = filter.createTimeMs;
            this.updateTimeMs = filter.updateTimeMs;
            this.createUser = filter.createUser;
            this.updateUser = filter.updateUser;
            this.analyticUuid = filter.analyticUuid;
            this.enabled = filter.enabled;
            this.expression = filter.expression;
            this.minMetaCreateTimeMs = filter.minMetaCreateTimeMs;
            this.maxMetaCreateTimeMs = filter.maxMetaCreateTimeMs;
            this.node = filter.node;
        }

        public Builder uuid(final String uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder version(final int version) {
            this.version = version;
            return this;
        }

        public Builder createTimeMs(final Long createTimeMs) {
            this.createTimeMs = createTimeMs;
            return this;
        }

        public Builder updateTimeMs(final Long updateTimeMs) {
            this.updateTimeMs = updateTimeMs;
            return this;
        }

        public Builder createUser(final String createUser) {
            this.createUser = createUser;
            return this;
        }

        public Builder updateUser(final String updateUser) {
            this.updateUser = updateUser;
            return this;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder analyticUuid(final String analyticUuid) {
            this.analyticUuid = analyticUuid;
            return this;
        }

        public Builder expression(final ExpressionOperator expression) {
            this.expression = expression;
            return this;
        }

        public Builder minMetaCreateTimeMs(final Long minMetaCreateTimeMs) {
            this.minMetaCreateTimeMs = minMetaCreateTimeMs;
            return this;
        }

        public Builder maxMetaCreateTimeMs(final Long maxMetaCreateTimeMs) {
            this.maxMetaCreateTimeMs = maxMetaCreateTimeMs;
            return this;
        }

        public Builder node(final String node) {
            this.node = node;
            return this;
        }

        public AnalyticProcessorFilter build() {
            return new AnalyticProcessorFilter(
                    uuid,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    analyticUuid,
                    enabled,
                    expression,
                    minMetaCreateTimeMs,
                    maxMetaCreateTimeMs,
                    node);
        }
    }
}
