package stroom.annotation.shared;

import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class ChangeAssignedTo extends AbstractAnnotationChange {

    @JsonProperty
    private final UserRef userRef;

    @JsonCreator
    public ChangeAssignedTo(@JsonProperty("userRef") final UserRef userRef) {
        this.userRef = userRef;
    }

    public UserRef getUserRef() {
        return userRef;
    }
}
