package stroom.pipeline.refdata.store;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class RefStoreEntry {

    @JsonProperty
    private final MapDefinition mapDefinition;
    @JsonProperty
    private final String key; // simple key or a str representation of a range (e.g. 1-100)
    @JsonProperty
    private final String value;
    @JsonProperty
    private int valueReferenceCount;
    @JsonProperty
    private final RefDataProcessingInfo refDataProcessingInfo;

    @JsonCreator
    public RefStoreEntry(@JsonProperty("mapDefinition") final MapDefinition mapDefinition,
                         @JsonProperty("key") final String key,
                         @JsonProperty("value") final String value,
                         @JsonProperty("valueReferenceCount") final int valueReferenceCount,
                         @JsonProperty("refDataProcessingInfo") final RefDataProcessingInfo refDataProcessingInfo) {

        this.mapDefinition = mapDefinition;
        this.key = key;
        this.value = value;
        this.valueReferenceCount = valueReferenceCount;
        this.refDataProcessingInfo = refDataProcessingInfo;
    }

    public MapDefinition getMapDefinition() {
        return mapDefinition;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public int getValueReferenceCount() {
        return valueReferenceCount;
    }

    public RefDataProcessingInfo getRefDataProcessingInfo() {
        return refDataProcessingInfo;
    }

    @Override
    public String toString() {
        return "RefStoreEntry{" +
                "mapDefinition=" + mapDefinition +
                ", key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", valueReferenceCount=" + valueReferenceCount +
                ", refDataProcessingInfo=" + refDataProcessingInfo +
                '}';
    }
}
