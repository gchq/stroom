package stroom.aws.s3.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class AwsProvidedContext {

    @JsonProperty
    private final String providerArn;
    @JsonProperty
    private final String contextAssertion;

    @JsonCreator
    public AwsProvidedContext(@JsonProperty("providerArn") final String providerArn,
                              @JsonProperty("contextAssertion") final String contextAssertion) {
        this.providerArn = providerArn;
        this.contextAssertion = contextAssertion;
    }

    public String getProviderArn() {
        return providerArn;
    }

    public String getContextAssertion() {
        return contextAssertion;
    }
}
