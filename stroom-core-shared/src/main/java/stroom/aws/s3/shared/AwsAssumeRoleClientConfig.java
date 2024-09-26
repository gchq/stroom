package stroom.aws.s3.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class AwsAssumeRoleClientConfig {
    @JsonProperty
    private final AwsCredentials credentials;
    @JsonProperty
    private final String region;
    @JsonProperty
    private final String endpointOverride;

    @JsonCreator
    public AwsAssumeRoleClientConfig(@JsonProperty("credentials") final AwsCredentials credentials,
                                     @JsonProperty("region") final String region,
                                     @JsonProperty("endpointOverride") final String endpointOverride) {
        this.credentials = credentials;
        this.region = region;
        this.endpointOverride = endpointOverride;
    }

    public AwsCredentials getCredentials() {
        return credentials;
    }

    public String getRegion() {
        return region;
    }

    public String getEndpointOverride() {
        return endpointOverride;
    }
}
