package stroom.volume;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class VolumeConfig {
    private int resilientReplicationCount = 1;
    private boolean preferLocalVolumes;
    private String volumeSelector = "RoundRobin";
    private boolean createDefaultOnStart;

    @JsonPropertyDescription("Set to determine how many volume locations will be used to store a single stream")
    public int getResilientReplicationCount() {
        return resilientReplicationCount;
    }

    public void setResilientReplicationCount(final int resilientReplicationCount) {
        this.resilientReplicationCount = resilientReplicationCount;
    }

    @JsonPropertyDescription("Should the stream store always attempt to write to local volumes before writing to remote ones?")
    public boolean isPreferLocalVolumes() {
        return preferLocalVolumes;
    }

    public void setPreferLocalVolumes(final boolean preferLocalVolumes) {
        this.preferLocalVolumes = preferLocalVolumes;
    }

    @JsonPropertyDescription("How should volumes be selected for use? Possible volume selectors include ('MostFreePercent', 'MostFree', 'Random', 'RoundRobinIgnoreLeastFreePercent', 'RoundRobinIgnoreLeastFree', 'RoundRobin', 'WeightedFreePercentRandom', 'WeightedFreeRandom') default is 'RoundRobin'")
    public String getVolumeSelector() {
        return volumeSelector;
    }

    public void setVolumeSelector(final String volumeSelector) {
        this.volumeSelector = volumeSelector;
    }

    @JsonPropertyDescription("If no existing volumes are present a default volume will be created on application start. The volume will live in the volumes sub directory of the Stroom installation directory")
    public boolean isCreateDefaultOnStart() {
        return createDefaultOnStart;
    }

    public void setCreateDefaultOnStart(final boolean createDefaultOnStart) {
        this.createDefaultOnStart = createDefaultOnStart;
    }
}
