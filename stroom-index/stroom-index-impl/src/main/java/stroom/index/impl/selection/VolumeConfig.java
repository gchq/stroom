package stroom.index.impl.selection;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;

import javax.inject.Singleton;
import javax.validation.constraints.Pattern;

@Singleton
public class VolumeConfig extends AbstractConfig {
    private static final String PATH_LIST_PATTERN = "^[^,]+(,[ ]?[^,]+)*$";
    private static final String NODE_LIST_PATTERN = "^[^ ,:@]+(,[ ]?[^ ,:@]+)*$";

    private int resilientReplicationCount = 1;
    private boolean preferLocalVolumes;
    private String volumeSelector = "RoundRobin";
    private boolean createDefaultIndexVolumesOnStart = true;
    private String defaultIndexVolumeGroupName = "Default Volume Group";
    private String defaultIndexVolumeGroupPaths = "volumes/defaultIndexVolume";
    private String defaultIndexVolumeGroupNodes = "node1a";
    private double defaultIndexVolumeFilesystemUtilisation = 0.9;

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
    @JsonPropertyDescription("If no existing index volume groups are present a default volume group will be created on application " +
            "start. Use property defaultIndexVolumeGroupName to define its name")
    public boolean isCreateDefaultIndexVolumesOnStart() {
        return createDefaultIndexVolumesOnStart;
    }

    public void setCreateDefaultIndexVolumesOnStart(final boolean createDefaultIndexVolumesOnStart) {
        this.createDefaultIndexVolumesOnStart = createDefaultIndexVolumesOnStart;
    }

    @JsonPropertyDescription("The name of the default index volume group that is created if none exist on application start. " +
            "Use properties defaultIndexVolumeGroupLimit, defaultIndexVolumeGroupPaths and defaultIndexVolumeGroupNodes to specify details.")
    public String getDefaultIndexVolumeGroupName(){
        return defaultIndexVolumeGroupName;
    }

    public void setDefaultIndexVolumeGroupName(final String defaultIndexVolumeGroupName) {
        this.defaultIndexVolumeGroupName = defaultIndexVolumeGroupName;
    }

    @JsonPropertyDescription("The paths on the nodes that hold the data and are created " +
            "on the defined list of nodes if the default index is created on application start. " +
            "N.B. It is possible to have multiple paths per node and/or the same path repeated on multiple nodes but " +
            "there must always be the same number of elements in this list as in property defaultIndexVolumeGroupNodes.")
    @Pattern(regexp = PATH_LIST_PATTERN, message = "Value must be a comma delimited string of paths")
    public String getDefaultIndexVolumeGroupPaths(){
        return defaultIndexVolumeGroupPaths;
    }

    public void setDefaultIndexVolumeGroupPaths(final String defaultIndexVolumeGroupPaths) {
        this.defaultIndexVolumeGroupPaths = defaultIndexVolumeGroupPaths;
    }

    @JsonPropertyDescription("The nodes associated with the paths that are created if " +
            "the default index is created on application start." +
            "N.B. It is possible to have multiple paths per node and/or the same path repeated on multiple nodes but " +
            "there must always be the same number of elements in this list as in property defaultIndexVolumeGroupNodes.")
    @Pattern(regexp = NODE_LIST_PATTERN, message = "Value must be a comma delimited string of node names")
    public String getDefaultIndexVolumeGroupNodes() {
        return defaultIndexVolumeGroupNodes;
    }

    public void setDefaultIndexVolumeGroupNodes(final String defaultIndexVolumeGroupNodes) {
        this.defaultIndexVolumeGroupNodes = defaultIndexVolumeGroupNodes;
    }

    @JsonPropertyDescription("Fraction of the filesystem beyond which the system will stop writing to the " +
            "default index volumes that may be created on application start.")
    public double getDefaultIndexVolumeFilesystemUtilisation() {
        return defaultIndexVolumeFilesystemUtilisation;
    }

    public void setDefaultIndexVolumeFilesystemUtilisation(final double defaultIndexVolumeFilesystemUtilisation) {
        this.defaultIndexVolumeFilesystemUtilisation = defaultIndexVolumeFilesystemUtilisation;
    }

    @Override
    public String toString() {
        return "VolumeConfig{" +
                "resilientReplicationCount=" + resilientReplicationCount +
                ", preferLocalVolumes=" + preferLocalVolumes +
                ", volumeSelector='" + volumeSelector + '\'' +
                ", createDefaultIndexVolumesOnStart=" + createDefaultIndexVolumesOnStart +
                ", defaultIndexVolumeGroupName=" + "\"" +defaultIndexVolumeGroupName + "\"" +
                ", defaultIndexVolumeFilesystemUtilisation=" + defaultIndexVolumeFilesystemUtilisation +
                ", defaultIndexVolumeGroupNodes=" + "\"" +defaultIndexVolumeGroupNodes + "\"" +
                ", defaultIndexVolumeGroupPaths=" + "\"" + defaultIndexVolumeGroupPaths + "\"" +
                '}';
    }
}
