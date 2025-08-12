package stroom.pathways.shared.otel.trace;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class AnyValue {

    @JsonProperty("stringValue")
    private final String stringValue;

    @JsonProperty("boolValue")
    private final Boolean boolValue;

    @JsonProperty("intValue")
    private final Integer intValue;

    @JsonProperty("doubleValue")
    private final Double doubleValue;

    @JsonProperty("arrayValue")
    private final ArrayValue arrayValue;

    @JsonProperty("kvlistValue")
    private final KeyValueList kvlistValue;

    @JsonProperty("bytesValue")
    private final String bytesValue;

    @JsonCreator
    public AnyValue(@JsonProperty("stringValue") final String stringValue,
                    @JsonProperty("boolValue") final Boolean boolValue,
                    @JsonProperty("intValue") final Integer intValue,
                    @JsonProperty("doubleValue") final Double doubleValue,
                    @JsonProperty("arrayValue") final ArrayValue arrayValue,
                    @JsonProperty("kvlistValue") final KeyValueList kvlistValue,
                    @JsonProperty("bytesValue") final String bytesValue) {
        this.stringValue = stringValue;
        this.boolValue = boolValue;
        this.intValue = intValue;
        this.doubleValue = doubleValue;
        this.arrayValue = arrayValue;
        this.kvlistValue = kvlistValue;
        this.bytesValue = bytesValue;
    }

    public String getStringValue() {
        return stringValue;
    }

    public Boolean getBoolValue() {
        return boolValue;
    }

    public Integer getIntValue() {
        return intValue;
    }

    public Double getDoubleValue() {
        return doubleValue;
    }

    public ArrayValue getArrayValue() {
        return arrayValue;
    }

    public KeyValueList getKvlistValue() {
        return kvlistValue;
    }

    public String getBytesValue() {
        return bytesValue;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AnyValue anyValue = (AnyValue) o;
        return Objects.equals(stringValue, anyValue.stringValue) &&
               Objects.equals(boolValue, anyValue.boolValue) &&
               Objects.equals(intValue, anyValue.intValue) &&
               Objects.equals(doubleValue, anyValue.doubleValue) &&
               Objects.equals(arrayValue, anyValue.arrayValue) &&
               Objects.equals(kvlistValue, anyValue.kvlistValue) &&
               Objects.equals(bytesValue, anyValue.bytesValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(stringValue, boolValue, intValue, doubleValue, arrayValue, kvlistValue, bytesValue);
    }

    @Override
    public String toString() {
        if (stringValue != null) {
            return "\"" + stringValue + "\"";
        }
        if (boolValue != null) {
            return boolValue.toString().toLowerCase(Locale.ROOT);
        }
        if (intValue != null) {
            return Integer.toString(intValue);
        }
        if (doubleValue != null) {
            return Double.toString(doubleValue);
        }
        if (arrayValue != null) {
            return arrayValue.toString();
        }
        if (kvlistValue != null) {
            return kvlistValue.toString();
        }
        if (bytesValue != null) {
            return bytesValue;
        }

        return "";
    }
}
