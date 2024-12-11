package stroom.aws.s3.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class AwsAssumeRoleRequest {

    @JsonProperty
    private final String roleArn;
    @JsonProperty
    private final String roleSessionName;
    @JsonProperty
    private final List<AwsPolicyDescriptorType> policyArns;
    @JsonProperty
    private final String policy;
    @JsonProperty
    private final Integer durationSeconds;
    @JsonProperty
    private final List<AwsTag> tags;
    @JsonProperty
    private final List<String> transitiveTagKeys;
    @JsonProperty
    private final String externalId;
    @JsonProperty
    private final String serialNumber;
    @JsonProperty
    private final String tokenCode;
    @JsonProperty
    private final String sourceIdentity;
    @JsonProperty
    private final List<AwsProvidedContext> providedContexts;

    @JsonCreator
    public AwsAssumeRoleRequest(@JsonProperty("roleArn") final String roleArn,
                                @JsonProperty("roleSessionName") final String roleSessionName,
                                @JsonProperty("policyArns") final List<AwsPolicyDescriptorType> policyArns,
                                @JsonProperty("policy") final String policy,
                                @JsonProperty("durationSeconds") final Integer durationSeconds,
                                @JsonProperty("tags") final List<AwsTag> tags,
                                @JsonProperty("transitiveTagKeys") final List<String> transitiveTagKeys,
                                @JsonProperty("externalId") final String externalId,
                                @JsonProperty("serialNumber") final String serialNumber,
                                @JsonProperty("tokenCode") final String tokenCode,
                                @JsonProperty("sourceIdentity") final String sourceIdentity,
                                @JsonProperty("providedContexts") final List<AwsProvidedContext> providedContexts) {
        this.roleArn = roleArn;
        this.roleSessionName = roleSessionName;
        this.policyArns = policyArns;
        this.policy = policy;
        this.durationSeconds = durationSeconds;
        this.tags = tags;
        this.transitiveTagKeys = transitiveTagKeys;
        this.externalId = externalId;
        this.serialNumber = serialNumber;
        this.tokenCode = tokenCode;
        this.sourceIdentity = sourceIdentity;
        this.providedContexts = providedContexts;
    }

    public String getRoleArn() {
        return roleArn;
    }

    public String getRoleSessionName() {
        return roleSessionName;
    }

    public List<AwsPolicyDescriptorType> getPolicyArns() {
        return policyArns;
    }

    public String getPolicy() {
        return policy;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }

    public List<AwsTag> getTags() {
        return tags;
    }

    public List<String> getTransitiveTagKeys() {
        return transitiveTagKeys;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public String getTokenCode() {
        return tokenCode;
    }

    public String getSourceIdentity() {
        return sourceIdentity;
    }

    public List<AwsProvidedContext> getProvidedContexts() {
        return providedContexts;
    }
}
