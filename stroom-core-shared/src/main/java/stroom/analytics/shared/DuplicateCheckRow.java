package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class DuplicateCheckRow {

    @JsonProperty
    private final List<String> values;

    @JsonCreator
    public DuplicateCheckRow(@JsonProperty("values") final List<String> values) {
        this.values = values;
    }

    public List<String> getValues() {
        return values;
    }
}
