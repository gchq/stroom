package stroom.aws.s3.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class AwsPolicyDescriptorType {
    @JsonProperty
    private final String arn;

    @JsonCreator
    public AwsPolicyDescriptorType(@JsonProperty("arn") final String arn) {
        this.arn = arn;
    }

    public String getArn() {
        return arn;
    }
}
