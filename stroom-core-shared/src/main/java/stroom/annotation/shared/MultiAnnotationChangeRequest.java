package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class MultiAnnotationChangeRequest {

    @JsonProperty
    private final List<Long> annotationIdList;
    @JsonProperty
    private final AbstractAnnotationChange change;

    @JsonCreator
    public MultiAnnotationChangeRequest(@JsonProperty("annotationIdList") final List<Long> annotationIdList,
                                        @JsonProperty("change") final AbstractAnnotationChange change) {
        this.annotationIdList = annotationIdList;
        this.change = change;
    }

    public List<Long> getAnnotationIdList() {
        return annotationIdList;
    }

    public AbstractAnnotationChange getChange() {
        return change;
    }
}
