package stroom.planb.impl.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class PlanBShardInfoResponse {

    @JsonProperty
    private final List<String[]> data;

    @JsonCreator
    public PlanBShardInfoResponse(@JsonProperty("data") final List<String[]> data) {
        this.data = data;
    }

    public List<String[]> getData() {
        return data;
    }
}
