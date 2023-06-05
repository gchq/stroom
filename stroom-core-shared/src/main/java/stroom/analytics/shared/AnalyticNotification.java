package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AnalyticNotification {

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
    private final AnalyticNotificationConfig config;

    @JsonCreator
    public AnalyticNotification(@JsonProperty("uuid") final String uuid,
                                @JsonProperty("version") final int version,
                                @JsonProperty("createTimeMs") final Long createTimeMs,
                                @JsonProperty("updateTimeMs") final Long updateTimeMs,
                                @JsonProperty("createUser") final String createUser,
                                @JsonProperty("updateUser") final String updateUser,
                                @JsonProperty("analyticUuid") final String analyticUuid,
                                @JsonProperty("enabled") final boolean enabled,
                                @JsonProperty("config") final AnalyticNotificationConfig config) {
        this.uuid = uuid;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.updateTimeMs = updateTimeMs;
        this.createUser = createUser;
        this.updateUser = updateUser;
        this.analyticUuid = analyticUuid;
        this.enabled = enabled;
        this.config = config;
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

    public AnalyticNotificationConfig getConfig() {
        return config;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnalyticNotification that = (AnalyticNotification) o;
        return enabled == that.enabled &&
                Objects.equals(uuid, that.uuid) &&
                Objects.equals(version, that.version) &&
                Objects.equals(createTimeMs, that.createTimeMs) &&
                Objects.equals(updateTimeMs, that.updateTimeMs) &&
                Objects.equals(createUser, that.createUser) &&
                Objects.equals(updateUser, that.updateUser) &&
                Objects.equals(analyticUuid, that.analyticUuid) &&
                Objects.equals(config, that.config);
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
                config);
    }

    @Override
    public String toString() {
        return "AnalyticNotification{" +
                "uuid='" + uuid + '\'' +
                ", version='" + version + '\'' +
                ", createTimeMs=" + createTimeMs +
                ", updateTimeMs=" + updateTimeMs +
                ", createUser='" + createUser + '\'' +
                ", updateUser='" + updateUser + '\'' +
                ", analyticUuid='" + analyticUuid + '\'' +
                ", enabled=" + enabled +
                ", config=" + config +
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
        private AnalyticNotificationConfig config;

        private Builder() {
        }

        private Builder(final AnalyticNotification notification) {
            this.uuid = notification.uuid;
            this.version = notification.version;
            this.createTimeMs = notification.createTimeMs;
            this.updateTimeMs = notification.updateTimeMs;
            this.createUser = notification.createUser;
            this.updateUser = notification.updateUser;
            this.analyticUuid = notification.analyticUuid;
            this.enabled = notification.enabled;
            this.config = notification.config;
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

        public Builder analyticUuid(final String analyticUuid) {
            this.analyticUuid = analyticUuid;
            return this;
        }

        public Builder enabled(final boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder config(final AnalyticNotificationConfig config) {
            this.config = config;
            return this;
        }

        public AnalyticNotification build() {
            return new AnalyticNotification(
                    uuid,
                    version,
                    createTimeMs,
                    updateTimeMs,
                    createUser,
                    updateUser,
                    analyticUuid,
                    enabled,
                    config);
        }
    }
}
