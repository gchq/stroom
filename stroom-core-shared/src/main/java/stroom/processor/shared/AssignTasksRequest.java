package stroom.processor.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class AssignTasksRequest {
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final int count;

    @JsonCreator
    public AssignTasksRequest(@JsonProperty("nodeName") final String nodeName,
                              @JsonProperty("count") final int count) {
        this.nodeName = nodeName;
        this.count = count;
    }

    public String getNodeName() {
        return nodeName;
    }

    public int getCount() {
        return count;
    }
}
