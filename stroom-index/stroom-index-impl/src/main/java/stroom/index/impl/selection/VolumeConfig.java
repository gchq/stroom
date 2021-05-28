package stroom.index.impl.selection;

import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.AbstractConfig;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;
import javax.inject.Singleton;

@Singleton
public class VolumeConfig extends AbstractConfig {

    public static final String PROP_NAME_DEFUALT_VOLUME_GROUP_NAME = "defaultIndexVolumeGroupName";
    private String volumeSelector = "RoundRobin";
    private boolean createDefaultIndexVolumesOnStart = true;
    private String defaultIndexVolumeGroupName = "Default Volume Group";
    private List<String> defaultIndexVolumeGroupPaths = List.of("volumes/default_index_volume");
    private double defaultIndexVolumeFilesystemUtilisation = 0.9;

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
    @JsonPropertyDescription("If no existing index volume groups are present a default volume group will be " +
            "created on application start. Use property defaultIndexVolumeGroupName to define its name")
    public boolean isCreateDefaultIndexVolumesOnStart() {
        return createDefaultIndexVolumesOnStart;
    }

    public void setCreateDefaultIndexVolumesOnStart(final boolean createDefaultIndexVolumesOnStart) {
        this.createDefaultIndexVolumesOnStart = createDefaultIndexVolumesOnStart;
    }

    @JsonPropertyDescription("The name of the default index volume group that is created if none exist on " +
            "application start. Use properties defaultIndexVolumeGroupLimit, defaultIndexVolumeGroupPaths " +
            "and defaultIndexVolumeGroupNodes to specify details.")
    public String getDefaultIndexVolumeGroupName() {
        return defaultIndexVolumeGroupName;
    }

    public void setDefaultIndexVolumeGroupName(final String defaultIndexVolumeGroupName) {
        this.defaultIndexVolumeGroupName = defaultIndexVolumeGroupName;
    }

    @JsonPropertyDescription("The paths on the nodes that hold the data and are created " +
            "on the defined list of nodes if the default index is created on application start. " +
            "N.B. It is possible to have multiple paths per node and/or the same path repeated on multiple " +
            "nodes but there must always be the same number of elements in this list as in property " +
            "defaultIndexVolumeGroupNodes. If a path is a relative path then it will be treated as being " +
            "relative to stroom.path.home.")
    public List<String> getDefaultIndexVolumeGroupPaths() {
        return defaultIndexVolumeGroupPaths;
    }

    public void setDefaultIndexVolumeGroupPaths(final List<String> defaultIndexVolumeGroupPaths) {
        this.defaultIndexVolumeGroupPaths = defaultIndexVolumeGroupPaths;
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
                ", volumeSelector='" + volumeSelector + '\'' +
                ", createDefaultIndexVolumesOnStart=" + createDefaultIndexVolumesOnStart +
                ", defaultIndexVolumeGroupName=" + "\"" + defaultIndexVolumeGroupName + "\"" +
                ", defaultIndexVolumeFilesystemUtilisation=" + defaultIndexVolumeFilesystemUtilisation +
                ", defaultIndexVolumeGroupPaths=" + "\"" + defaultIndexVolumeGroupPaths + "\"" +
                '}';
    }
}
