package stroom.aws.s3.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class AwsBasicCredentials implements AwsCredentials {

    @JsonProperty
    private final String accessKeyId;
    @JsonProperty
    private final String secretAccessKey;

    @JsonCreator
    public AwsBasicCredentials(@JsonProperty("accessKeyId") final String accessKeyId,
                               @JsonProperty("secretAccessKey") final String secretAccessKey) {
        this.accessKeyId = accessKeyId;
        this.secretAccessKey = secretAccessKey;
    }

    public String getAccessKeyId() {
        return accessKeyId;
    }

    public String getSecretAccessKey() {
        return secretAccessKey;
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
        final AwsBasicCredentials that = (AwsBasicCredentials) o;
        return Objects.equals(accessKeyId, that.accessKeyId) && Objects.equals(secretAccessKey,
                that.secretAccessKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accessKeyId, secretAccessKey);
    }

    @Override
    public String toString() {
        return "AwsBasicCredentials{" +
                "accessKeyId='" + accessKeyId + '\'' +
                ", secretAccessKey='" + secretAccessKey + '\'' +
                '}';
    }

    public static class Builder {

        private String accessKeyId;
        private String secretAccessKey;

        public Builder() {
        }

        public Builder(final AwsBasicCredentials awsBasicCredentials) {
            this.accessKeyId = awsBasicCredentials.accessKeyId;
            this.secretAccessKey = awsBasicCredentials.secretAccessKey;
        }

        public Builder accessKeyId(final String accessKeyId) {
            this.accessKeyId = accessKeyId;
            return this;
        }

        public Builder secretAccessKey(final String secretAccessKey) {
            this.secretAccessKey = secretAccessKey;
            return this;
        }

        public AwsBasicCredentials build() {
            return new AwsBasicCredentials(
                    accessKeyId,
                    secretAccessKey);
        }
    }
}
