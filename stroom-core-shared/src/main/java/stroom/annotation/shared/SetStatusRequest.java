package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class SetStatusRequest {
    @JsonProperty
    private final List<Long> annotationIdList;
    @JsonProperty
    private final String status;

    @JsonCreator
    public SetStatusRequest(@JsonProperty("annotationIdList") final List<Long> annotationIdList,
                            @JsonProperty("status") final String status) {
        this.annotationIdList = annotationIdList;
        this.status = status;
    }

    public List<Long> getAnnotationIdList() {
        return annotationIdList;
    }

    public String getStatus() {
        return status;
    }
}
