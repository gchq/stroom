package stroom.index.impl.selection;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;

@Singleton
public class VolumeConfig extends AbstractConfig {
    private int resilientReplicationCount = 1;
    private boolean preferLocalVolumes;
    private String volumeSelector = "RoundRobin";
    private boolean createDefaultOnStart = true;
    private String defaultVolumeGroupName = "Default Volume Group";
    private String defaultVolumeGroupPaths = "volumes/defaultIndexVolume";
    private String defaultVolumeGroupNodes = "node1a";
    private String defaultVolumeGroupLimit = "1G";

    @JsonPropertyDescription("Set to determine how many volume locations will be used to store a single stream")
    public int getResilientReplicationCount() {
        return resilientReplicationCount;
    }

    public void setResilientReplicationCount(final int resilientReplicationCount) {
        this.resilientReplicationCount = resilientReplicationCount;
    }

    @JsonPropertyDescription("Should the stream store always attempt to write to local volumes before writing to " +
            "remote ones?")
    public boolean isPreferLocalVolumes() {
        return preferLocalVolumes;
    }

    public void setPreferLocalVolumes(final boolean preferLocalVolumes) {
        this.preferLocalVolumes = preferLocalVolumes;
    }

    @JsonPropertyDescription("How should volumes be selected for use? Possible volume selectors " +
            "include ('MostFreePercent', 'MostFree', 'Random', 'RoundRobinIgnoreLeastFreePercent', " +
            "'RoundRobinIgnoreLeastFree', 'RoundRobin', 'WeightedFreePercentRandom', 'WeightedFreeRandom') " +
            "default is 'RoundRobin'")
    public String getVolumeSelector() {
        return volumeSelector;
    }

    public void setVolumeSelector(final String volumeSelector) {
        this.volumeSelector = volumeSelector;
    }

    @RequiresRestart(RequiresRestart.RestartScope.UI)
    @JsonPropertyDescription("If no existing volume groups are present a default volume group will be created on application " +
            "start.")
    public boolean isCreateDefaultOnStart() {
        return createDefaultOnStart;
    }

    public void setCreateDefaultOnStart(final boolean createDefaultOnStart) {
        this.createDefaultOnStart = createDefaultOnStart;
    }

    @JsonPropertyDescription("The name of the default index volume group that is created if none exist on application start.")
    public String getDefaultVolumeGroupName(){
        return defaultVolumeGroupName;
    }

    public void setDefaultVolumeGroupName(final String defaultVolumeGroupName) {
        this.defaultVolumeGroupName = defaultVolumeGroupName;
    }

    @JsonPropertyDescription("Comma delimited list of the paths on the nodes that hold the data and are created " +
            "on the defined list of nodes if the default index is created on application start. " +
            "N.B. It is possible to have multiple paths per node and/or the same path repeated on multiple nodes but " +
            "there must always be the same number of elements in this list as in property defaultVolumeGroupNodes.")
    public String getDefaultVolumeGroupPaths(){
        return defaultVolumeGroupPaths;
    }

    public void setDefaultVolumeGroupPaths(final String defaultVolumeGroupPaths) {
        this.defaultVolumeGroupPaths = defaultVolumeGroupPaths;
    }

    @JsonPropertyDescription("Comma delimited list of the nodes associated with the paths that are created if " +
            "the default index is created on application start." +
            "N.B. It is possible to have multiple paths per node and/or the same path repeated on multiple nodes but " +
            "there must always be the same number of elements in this list as in property defaultVolumeGroupNodes.")
    public String getDefaultVolumeGroupNodes() {
        return defaultVolumeGroupNodes;
    }

    public void setDefaultVolumeGroupNodes(final String defaultVolumeGroupNodes) {
        this.defaultVolumeGroupNodes = defaultVolumeGroupNodes;
    }

    @JsonPropertyDescription("The size limit that will be applied to all the volumes in the default index " +
            "volume group that is created if none exist on application start.  ")
    public String getDefaultVolumeGroupLimit() {
        return defaultVolumeGroupLimit;
    }

    public void setDefaultVolumeGroupLimit(final String defaultVolumeGroupLimit) {
        this.defaultVolumeGroupLimit = defaultVolumeGroupLimit;
    }

    @Override
    public String toString() {
        return "VolumeConfig{" +
                "resilientReplicationCount=" + resilientReplicationCount +
                ", preferLocalVolumes=" + preferLocalVolumes +
                ", volumeSelector='" + volumeSelector + '\'' +
                ", createDefaultOnStart=" + createDefaultOnStart +
                ", defaultVolumeGroupName=" + defaultVolumeGroupName +
                ", defaultVolumeGroupLimit=" + defaultVolumeGroupLimit +
                ", defaultVolumeGroupNodes=" + defaultVolumeGroupNodes +
                ", defaultVolumeGroupPaths=" + defaultVolumeGroupPaths +
                '}';
    }
}
