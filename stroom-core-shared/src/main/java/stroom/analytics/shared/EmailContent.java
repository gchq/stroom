package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class EmailContent {

    @JsonProperty
    private final String subject;
    @JsonProperty
    private final String body;

    @JsonCreator
    public EmailContent(@JsonProperty("subject") final String subject,
                        @JsonProperty("body") final String body) {
        this.subject = subject;
        this.body = body;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "EmailContent{" +
               "subject='" + subject + '\'' +
               ", body='" + body + '\'' +
               '}';
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        final EmailContent that = (EmailContent) object;
        return Objects.equals(subject, that.subject) && Objects.equals(body, that.body);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, body);
    }
}
