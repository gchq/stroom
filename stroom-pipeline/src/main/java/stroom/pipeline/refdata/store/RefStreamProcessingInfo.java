package stroom.pipeline.refdata.store;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class RefStreamProcessingInfo {

    @JsonProperty
    private final RefStreamDefinition refStreamDefinition;
    @JsonProperty
    private final RefDataProcessingInfo refDataProcessingInfo;
    @JsonProperty
    private final Set<String> mapNames;

    @JsonCreator
    public RefStreamProcessingInfo(
            @JsonProperty("refStreamDefinition") final RefStreamDefinition refStreamDefinition,
            @JsonProperty("refDataProcessingInfo") final RefDataProcessingInfo refDataProcessingInfo,
            @JsonProperty("mapNames") final Set<String> mapNames) {
        this.refStreamDefinition = Objects.requireNonNull(refStreamDefinition);
        this.refDataProcessingInfo = Objects.requireNonNull(refDataProcessingInfo);
        this.mapNames = Objects.requireNonNull(mapNames);
    }

    public RefStreamDefinition getRefStreamDefinition() {
        return refStreamDefinition;
    }

    public RefDataProcessingInfo getRefDataProcessingInfo() {
        return refDataProcessingInfo;
    }

    public Set<String> getMapNames() {
        return mapNames;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RefStreamProcessingInfo info = (RefStreamProcessingInfo) o;
        return refStreamDefinition.equals(info.refStreamDefinition)
                && refDataProcessingInfo.equals(info.refDataProcessingInfo)
                && mapNames.equals(info.mapNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(refStreamDefinition, refDataProcessingInfo, mapNames);
    }

    @Override
    public String toString() {
        return "RefStreamProcessingInfo{" +
                "refStreamDefinition=" + refStreamDefinition +
                ", refDataProcessingInfo=" + refDataProcessingInfo +
                ", mapNames=" + mapNames +
                '}';
    }
}
