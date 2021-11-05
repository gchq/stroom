package stroom.pipeline.refdata;

import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;
import javax.validation.constraints.Min;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class ReferenceDataConfig extends AbstractConfig {

    private int maxPutsBeforeCommit = 200_000;
    private int maxPurgeDeletesBeforeCommit = 200_000;
    private StroomDuration purgeAge = StroomDuration.ofDays(30);
    private int loadingLockStripes = 2048;

    private ReferenceDataLmdbConfig lmdbConfig = new ReferenceDataLmdbConfig();

    private CacheConfig effectiveStreamCache = CacheConfig.builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofMinutes(10))
            .build();

    @Min(0)
    @JsonPropertyDescription("The maximum number of puts into the store (in a single load) before the " +
            "transaction is committed. There is only one write transaction available at a time so reducing " +
            "this value allows multiple loads to potentially each load a chunk at a time. However, load times " +
            "increase rapidly with values below around 2,000. For maximum performance of a single load set this " +
            "value to 0 to only commit at the very end of the load.")
    public int getMaxPutsBeforeCommit() {
        return maxPutsBeforeCommit;
    }

    @SuppressWarnings("unused")
    public void setMaxPutsBeforeCommit(final int maxPutsBeforeCommit) {
        this.maxPutsBeforeCommit = maxPutsBeforeCommit;
    }

    @Min(0)
    @JsonPropertyDescription("The maximum number of entries in one reference stream to purge before the " +
            "transaction is committed. A value high enough to purge all entries in one transaction is " +
            "preferable but for large reference streams this may result in errors due to the transaction " +
            "being too large.")
    public int getMaxPurgeDeletesBeforeCommit() {
        return maxPurgeDeletesBeforeCommit;
    }

    @SuppressWarnings("unused")
    public void setMaxPurgeDeletesBeforeCommit(final int maxPurgeDeletesBeforeCommit) {
        this.maxPurgeDeletesBeforeCommit = maxPurgeDeletesBeforeCommit;
    }

    @JsonPropertyDescription("The time to retain reference data for in the off heap store. The time is taken " +
            "from the time that the reference stream was last accessed, e.g. a lookup was made against it. " +
            "In ISO-8601 duration format, e.g. 'P1DT12H'")
    public StroomDuration getPurgeAge() {
        return purgeAge;
    }

    @SuppressWarnings("unused")
    public void setPurgeAge(final StroomDuration purgeAge) {
        this.purgeAge = purgeAge;
    }

    @Min(2)
    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription("The number of lock stripes used for preventing multiple pipeline processes " +
            "from loading the same reference stream at the same time. Values should be a power of 2. " +
            "Lower values will mean it is more likely for two different streams from blocking one another.")
    public int getLoadingLockStripes() {
        return loadingLockStripes;
    }

    public void setLoadingLockStripes(final int loadingLockStripes) {
        this.loadingLockStripes = loadingLockStripes;
    }

    @JsonProperty("lmdb")
    public ReferenceDataLmdbConfig getLmdbConfig() {
        return lmdbConfig;
    }

    @SuppressWarnings("unused")
    public void setLmdbConfig(final ReferenceDataLmdbConfig lmdbConfig) {
        this.lmdbConfig = lmdbConfig;
    }

    public CacheConfig getEffectiveStreamCache() {
        return effectiveStreamCache;
    }

    @SuppressWarnings("unused")
    public void setEffectiveStreamCache(final CacheConfig effectiveStreamCache) {
        this.effectiveStreamCache = effectiveStreamCache;
    }

    @Override
    public String toString() {
        return "ReferenceDataConfig{" +
                "maxPutsBeforeCommit=" + maxPutsBeforeCommit +
                ", maxPurgeDeletesBeforeCommit=" + maxPurgeDeletesBeforeCommit +
                ", purgeAge=" + purgeAge +
                ", loadingLockStripes=" + loadingLockStripes +
                ", lmdbConfig=" + lmdbConfig +
                ", effectiveStreamCache=" + effectiveStreamCache +
                '}';
    }
}
