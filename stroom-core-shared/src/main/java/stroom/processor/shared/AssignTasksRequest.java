package stroom.processor.shared;

import stroom.task.shared.TaskId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class AssignTasksRequest {

    @JsonProperty
    private final TaskId sourceTaskId;
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final int count;

    @JsonCreator
    public AssignTasksRequest(@JsonProperty("sourceTaskId") final TaskId sourceTaskId,
                              @JsonProperty("nodeName") final String nodeName,
                              @JsonProperty("count") final int count) {
        this.sourceTaskId = sourceTaskId;
        this.nodeName = nodeName;
        this.count = count;
    }

    public TaskId getSourceTaskId() {
        return sourceTaskId;
    }

    public String getNodeName() {
        return nodeName;
    }

    public int getCount() {
        return count;
    }

    @Override
    public String toString() {
        return "AssignTasksRequest{" +
                "sourceTaskId=" + sourceTaskId +
                ", nodeName='" + nodeName + '\'' +
                ", count=" + count +
                '}';
    }
}
