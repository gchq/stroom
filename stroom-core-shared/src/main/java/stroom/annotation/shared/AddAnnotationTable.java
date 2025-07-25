package stroom.annotation.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public final class AddAnnotationTable extends AbstractAnnotationChange {

    @JsonProperty
    private final AnnotationTable table;

    @JsonCreator
    public AddAnnotationTable(@JsonProperty("table") final AnnotationTable table) {
        this.table = table;
    }

    public AnnotationTable getTable() {
        return table;
    }
}
