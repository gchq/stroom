package stroom.data.store.impl.fs;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;
import java.util.concurrent.TimeUnit;

@Singleton
public class FsVolumeConfig implements IsConfig {
//    private int resilientReplicationCount = 1;
//    private boolean preferLocalVolumes;
    private String volumeSelector = "RoundRobin";
    private boolean createDefaultOnStart = true;

    private CacheConfig feedPathCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();
    private CacheConfig typePathCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build();

//    @JsonPropertyDescription("Set to determine how many volume locations will be used to store a single stream")
//    public int getResilientReplicationCount() {
//        return resilientReplicationCount;
//    }
//
//    public void setResilientReplicationCount(final int resilientReplicationCount) {
//        this.resilientReplicationCount = resilientReplicationCount;
//    }
//
//    @JsonPropertyDescription("Should the stream store always attempt to write to local volumes before writing to " +
//            "remote ones?")
//    public boolean isPreferLocalVolumes() {
//        return preferLocalVolumes;
//    }
//
//    public void setPreferLocalVolumes(final boolean preferLocalVolumes) {
//        this.preferLocalVolumes = preferLocalVolumes;
//    }

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
    @JsonPropertyDescription("If no existing volumes are present a default volume will be created on application " +
            "start. The volume will live in the volumes sub directory of the Stroom installation directory")
    public boolean isCreateDefaultOnStart() {
        return createDefaultOnStart;
    }

    public void setCreateDefaultOnStart(final boolean createDefaultOnStart) {
        this.createDefaultOnStart = createDefaultOnStart;
    }

    public CacheConfig getFeedPathCache() {
        return feedPathCache;
    }

    public void setFeedPathCache(final CacheConfig feedPathCache) {
        this.feedPathCache = feedPathCache;
    }

    public CacheConfig getTypePathCache() {
        return typePathCache;
    }

    public void setTypePathCache(final CacheConfig typePathCache) {
        this.typePathCache = typePathCache;
    }

    @Override
    public String toString() {
        return "VolumeConfig{" +
                "volumeSelector='" + volumeSelector + '\'' +
                ", createDefaultOnStart=" + createDefaultOnStart +
                '}';
    }
}
