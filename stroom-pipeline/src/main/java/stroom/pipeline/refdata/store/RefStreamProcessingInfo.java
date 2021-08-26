package stroom.pipeline.refdata.store;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class RefStreamProcessingInfo {

    private final RefStreamDefinition refStreamDefinition;
    private final RefDataProcessingInfo refDataProcessingInfo;
    private final Set<String> mapNames;

    public RefStreamProcessingInfo(final RefStreamDefinition refStreamDefinition,
                                   final RefDataProcessingInfo refDataProcessingInfo,
                                   final Set<String> mapNames) {
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
