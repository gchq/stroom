package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = VariableTypeValue.class, name = "anyValue"),
        @JsonSubTypes.Type(value = NanoTimeRange.class, name = "durationRange"),
        @JsonSubTypes.Type(value = StringValue.class, name = "stringValue"),
        @JsonSubTypes.Type(value = StringSet.class, name = "stringSet"),
        @JsonSubTypes.Type(value = StringPattern.class, name = "stringPattern"),
        @JsonSubTypes.Type(value = BooleanValue.class, name = "booleanValue"),
        @JsonSubTypes.Type(value = BooleanSet.class, name = "booleanSet"),
        @JsonSubTypes.Type(value = IntegerValue.class, name = "integerValue"),
        @JsonSubTypes.Type(value = IntegerSet.class, name = "integerSet"),
        @JsonSubTypes.Type(value = IntegerRange.class, name = "integerRange"),
        @JsonSubTypes.Type(value = DoubleValue.class, name = "doubleValue"),
        @JsonSubTypes.Type(value = DoubleSet.class, name = "doubleSet"),
        @JsonSubTypes.Type(value = DoubleRange.class, name = "doubleRange")
})
public sealed interface Constraint permits
        VariableTypeValue,
        NanoTimeRange,
        StringValue,
        StringSet,
        StringPattern,
        BooleanValue,
        BooleanSet,
        IntegerValue,
        IntegerSet,
        IntegerRange,
        DoubleValue,
        DoubleSet,
        DoubleRange {

}
