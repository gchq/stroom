package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class ChangeSubject extends AbstractAnnotationChange {

    @JsonProperty
    private final String subject;

    @JsonCreator
    public ChangeSubject(@JsonProperty("subject") final String subject) {
        this.subject = subject;
    }

    public String getSubject() {
        return subject;
    }
}
