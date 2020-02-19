package stroom.processor.shared;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import stroom.util.shared.TreeRow;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = ProcessorRow.class, name = "processor"),
        @JsonSubTypes.Type(value = ProcessorFilterRow.class, name = "processorFilter")
})
public abstract class ProcessorListRow implements TreeRow {
}
