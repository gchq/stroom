package stroom.aws.s3.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class AwsAssumeRole {

    @JsonProperty
    private final AwsAssumeRoleClientConfig clientConfig;
    @JsonProperty
    private final AwsAssumeRoleRequest request;

    @JsonCreator
    public AwsAssumeRole(@JsonProperty("clientConfig") final AwsAssumeRoleClientConfig clientConfig,
                         @JsonProperty("request") final AwsAssumeRoleRequest request) {
        this.clientConfig = clientConfig;
        this.request = request;
    }

    public AwsAssumeRoleClientConfig getClientConfig() {
        return clientConfig;
    }

    public AwsAssumeRoleRequest getRequest() {
        return request;
    }
}
