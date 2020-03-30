package stroom.meta.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class UpdateStatusRequest {
    @JsonProperty
    private final FindMetaCriteria criteria;
    @JsonProperty
    private final Status newStatus;

    @JsonCreator
    public UpdateStatusRequest(@JsonProperty("criteria") final FindMetaCriteria criteria,
                               @JsonProperty("newStatus") final Status newStatus) {
        this.criteria = criteria;
        this.newStatus = newStatus;
    }

    public FindMetaCriteria getCriteria() {
        return criteria;
    }

    public Status getNewStatus() {
        return newStatus;
    }
}
