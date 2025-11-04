package stroom.pathways.shared.pathway;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AnyTypeValue.class, name = "anyValue"),
        @JsonSubTypes.Type(value = NanoTimeValue.class, name = "duration"),
        @JsonSubTypes.Type(value = NanoTimeRange.class, name = "durationRange"),
        @JsonSubTypes.Type(value = StringValue.class, name = "string"),
        @JsonSubTypes.Type(value = StringSet.class, name = "stringSet"),
        @JsonSubTypes.Type(value = Regex.class, name = "regex"),
        @JsonSubTypes.Type(value = BooleanValue.class, name = "boolean"),
        @JsonSubTypes.Type(value = AnyBoolean.class, name = "booleanSet"),
        @JsonSubTypes.Type(value = IntegerValue.class, name = "integer"),
        @JsonSubTypes.Type(value = IntegerSet.class, name = "integerSet"),
        @JsonSubTypes.Type(value = IntegerRange.class, name = "integerRange"),
        @JsonSubTypes.Type(value = DoubleValue.class, name = "double"),
        @JsonSubTypes.Type(value = DoubleSet.class, name = "doubleSet"),
        @JsonSubTypes.Type(value = DoubleRange.class, name = "doubleRange")
})
public sealed interface ConstraintValue permits
        AnyTypeValue,
        NanoTimeValue,
        NanoTimeRange,
        StringValue,
        StringSet,
        Regex,
        BooleanValue,
        AnyBoolean,
        IntegerValue,
        IntegerSet,
        IntegerRange,
        DoubleValue,
        DoubleSet,
        DoubleRange {

    ConstraintValueType valueType();
}
