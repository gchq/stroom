package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ChangeStatus extends AbstractAnnotationChange {

    @JsonProperty
    private final String status;

    @JsonCreator
    public ChangeStatus(@JsonProperty("status") final String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }
}
