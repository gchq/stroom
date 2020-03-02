package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_DEFAULT)
public class SetAssignedToRequest {
    @JsonProperty
    private final List<Long> annotationIdList;
    @JsonProperty
    private final String assignedTo;

    @JsonCreator
    public SetAssignedToRequest(@JsonProperty("annotationIdList") final List<Long> annotationIdList,
                                @JsonProperty("assignedTo") final String assignedTo) {
        this.annotationIdList = annotationIdList;
        this.assignedTo = assignedTo;
    }

    public List<Long> getAnnotationIdList() {
        return annotationIdList;
    }

    public String getAssignedTo() {
        return assignedTo;
    }
}
