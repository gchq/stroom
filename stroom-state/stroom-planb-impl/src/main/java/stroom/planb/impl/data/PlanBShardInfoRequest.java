package stroom.planb.impl.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class PlanBShardInfoRequest {

    @JsonProperty
    private final String[] fields;

    @JsonCreator
    public PlanBShardInfoRequest(@JsonProperty("fields") final String[] fields) {
        this.fields = fields;
    }

    public String[] getFields() {
        return fields;
    }
}
