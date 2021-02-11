package stroom.processor.shared;

import stroom.util.shared.TreeRow;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @Type(value = ProcessorRow.class, name = "processor"),
        @Type(value = ProcessorFilterRow.class, name = "processorFilter")
})
public abstract class ProcessorListRow implements TreeRow {

}
