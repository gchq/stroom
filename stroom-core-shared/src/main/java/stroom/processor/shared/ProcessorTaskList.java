package stroom.processor.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class ProcessorTaskList {
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final List<ProcessorTask> list;

    @JsonCreator
    public ProcessorTaskList(@JsonProperty("nodeName") final String nodeName,
                             @JsonProperty("list") final List<ProcessorTask> list) {
        this.nodeName = nodeName;
        this.list = list;
    }

    public String getNodeName() {
        return nodeName;
    }

    public List<ProcessorTask> getList() {
        return list;
    }
}
