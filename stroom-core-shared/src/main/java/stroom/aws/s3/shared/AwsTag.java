package stroom.aws.s3.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class AwsTag {

    @JsonProperty
    private final String key;
    @JsonProperty
    private final String value;

    @JsonCreator
    public AwsTag(@JsonProperty("key") final String key,
                  @JsonProperty("value") final String value) {
        this.key = key;
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }
}
