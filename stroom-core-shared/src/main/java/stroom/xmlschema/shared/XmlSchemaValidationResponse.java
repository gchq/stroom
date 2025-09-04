package stroom.xmlschema.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class XmlSchemaValidationResponse {

    @JsonProperty
    private final boolean ok;
    @JsonProperty
    private final String error;

    @JsonCreator
    public XmlSchemaValidationResponse(@JsonProperty("ok") final boolean ok,
                                       @JsonProperty("error") final String error) {
        this.ok = ok;
        this.error = error;
    }

    public boolean isOk() {
        return ok;
    }

    public String getError() {
        return error;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final XmlSchemaValidationResponse that = (XmlSchemaValidationResponse) o;
        return ok == that.ok &&
               Objects.equals(error, that.error);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ok, error);
    }

    @Override
    public String toString() {
        return "XmlSchemaValidationResponse{" +
               "ok=" + ok +
               ", error='" + error + '\'' +
               '}';
    }
}
