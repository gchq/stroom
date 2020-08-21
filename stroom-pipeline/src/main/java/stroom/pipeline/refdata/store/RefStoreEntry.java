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
    private final RefDataProcessingInfo refDataProcessingInfo;

    @JsonCreator
    public RefStoreEntry(@JsonProperty("mapDefinition") final MapDefinition mapDefinition,
                         @JsonProperty("key") final String key,
                         @JsonProperty("value") final String value,
                         @JsonProperty("refDataProcessingInfo") final RefDataProcessingInfo refDataProcessingInfo) {

        this.mapDefinition = mapDefinition;
        this.key = key;
        this.value = value;
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

    public RefDataProcessingInfo getRefDataProcessingInfo() {
        return refDataProcessingInfo;
    }
}
