package stroom.importexport.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.DocRef;

@JsonPropertyOrder({"from", "to", "ok"})
@JsonInclude(Include.NON_DEFAULT)
public class Dependency {
    @JsonProperty
    private DocRef from;
    @JsonProperty
    private DocRef to;
    @JsonProperty
    private boolean ok;

    @JsonCreator
    public Dependency(@JsonProperty("from") final DocRef from,
                      @JsonProperty("to") final DocRef to,
                      @JsonProperty("ok") final boolean ok) {
        this.from = from;
        this.to = to;
        this.ok = ok;
    }

    public DocRef getFrom() {
        return from;
    }

    public DocRef getTo() {
        return to;
    }

    public boolean isOk() {
        return ok;
    }
}
