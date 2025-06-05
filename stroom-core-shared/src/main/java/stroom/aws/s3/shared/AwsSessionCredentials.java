package stroom.aws.s3.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public final class AwsSessionCredentials implements AwsCredentials {

    @JsonProperty
    private final String accessKeyId;
    @JsonProperty
    private final String secretAccessKey;
    @JsonProperty
    private final String sessionToken;

    @JsonCreator
    public AwsSessionCredentials(@JsonProperty("accessKeyId") final String accessKeyId,
                                 @JsonProperty("secretAccessKey") final String secretAccessKey,
                                 @JsonProperty("sessionToken") final String sessionToken) {
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
        this.sessionToken = sessionToken;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AwsSessionCredentials that = (AwsSessionCredentials) o;
        return Objects.equals(accessKeyId, that.accessKeyId) && Objects.equals(secretAccessKey,
                that.secretAccessKey) && Objects.equals(sessionToken, that.sessionToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessKeyId, secretAccessKey, sessionToken);
    }

    @Override
    public String toString() {
        return "AwsSessionCredentials{" +
                "accessKeyId='" + accessKeyId + '\'' +
                ", secretAccessKey='" + secretAccessKey + '\'' +
                ", sessionToken='" + sessionToken + '\'' +
                '}';
    }

    public static class Builder {

        private String accessKeyId;
        private String secretAccessKey;
        private String sessionToken;

        public Builder() {
        }

        public Builder(final AwsSessionCredentials awsSessionCredentials) {
            this.accessKeyId = awsSessionCredentials.accessKeyId;
            this.secretAccessKey = awsSessionCredentials.secretAccessKey;
            this.sessionToken = awsSessionCredentials.sessionToken;
        }

        public Builder accessKeyId(final String accessKeyId) {
            this.accessKeyId = accessKeyId;
            return this;
        }

        public Builder secretAccessKey(final String secretAccessKey) {
            this.secretAccessKey = secretAccessKey;
            return this;
        }

        public Builder sessionToken(final String sessionToken) {
            this.sessionToken = sessionToken;
            return this;
        }

        public AwsSessionCredentials build() {
            return new AwsSessionCredentials(
                    accessKeyId,
                    secretAccessKey,
                    sessionToken);
        }
    }
}
