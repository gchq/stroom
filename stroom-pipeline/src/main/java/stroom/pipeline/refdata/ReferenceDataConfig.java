package stroom.pipeline.refdata;

import stroom.pipeline.refdata.store.RefDataStoreModule.RefDataPurge;
import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresRestart;
import stroom.util.config.annotations.RequiresRestart.RestartScope;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.Min;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class ReferenceDataConfig extends AbstractConfig implements IsStroomConfig {

    public static final boolean DEFAULT_AUTO_PURGE_ENABLED = true;
    public static final boolean DEFAULT_COMPACT_AFTER_PURGE_ENABLED = false;

    private final int maxPutsBeforeCommit;
    private final int maxPurgeDeletesBeforeCommit;
    private final StroomDuration purgeAge;
    private final boolean autoPurgeEnabled;
    private final boolean compactAfterPurgeEnabled;
    private final int loadingLockStripes;
    private final ReferenceDataLmdbConfig lmdbConfig;
    private final ReferenceDataStagingLmdbConfig stagingLmdbConfig;
    private final CacheConfig effectiveStreamCache;
    private final CacheConfig metaIdToRefStoreCache;

    public ReferenceDataConfig() {
        maxPutsBeforeCommit = 200_000;
        maxPurgeDeletesBeforeCommit = 200_000;
        purgeAge = StroomDuration.ofDays(30);
        autoPurgeEnabled = DEFAULT_AUTO_PURGE_ENABLED;
        compactAfterPurgeEnabled = DEFAULT_COMPACT_AFTER_PURGE_ENABLED;
//        maxCombinedStoreSize = ByteSize.ZERO;
        loadingLockStripes = 2048;
        lmdbConfig = new ReferenceDataLmdbConfig();
        stagingLmdbConfig = new ReferenceDataStagingLmdbConfig();

        effectiveStreamCache = CacheConfig.builder()
                .maximumSize(1_000L)
                .expireAfterWrite(StroomDuration.ofMinutes(10))
                .build();

        // Mappings are immutable so can hang on to them for a long time
        metaIdToRefStoreCache = CacheConfig.builder()
                .maximumSize(1_000L)
                .expireAfterAccess(StroomDuration.ofHours(1))
                .build();
    }

    @JsonCreator
    public ReferenceDataConfig(@JsonProperty("maxPutsBeforeCommit") final int maxPutsBeforeCommit,
                               @JsonProperty("maxPurgeDeletesBeforeCommit") final int maxPurgeDeletesBeforeCommit,
                               @JsonProperty("purgeAge") final StroomDuration purgeAge,
                               @JsonProperty("autoPurgeEnabled") final Boolean autoPurgeEnabled,
                               @JsonProperty("compactAfterPurgeEnabled") final Boolean compactAfterPurgeEnabled,
                               @JsonProperty("loadingLockStripes") final int loadingLockStripes,
                               @JsonProperty("lmdb") final ReferenceDataLmdbConfig lmdbConfig,
                               @JsonProperty("stagingLmdb") final ReferenceDataStagingLmdbConfig stagingLmdbConfig,
                               @JsonProperty("effectiveStreamCache") final CacheConfig effectiveStreamCache,
                               @JsonProperty("metaIdToRefStoreCache") final CacheConfig metaIdToRefStoreCache) {
        this.maxPutsBeforeCommit = maxPutsBeforeCommit;
        this.maxPurgeDeletesBeforeCommit = maxPurgeDeletesBeforeCommit;
        this.purgeAge = purgeAge;
        this.autoPurgeEnabled = Objects.requireNonNullElse(autoPurgeEnabled, DEFAULT_AUTO_PURGE_ENABLED);
        this.compactAfterPurgeEnabled = Objects.requireNonNullElse(
                compactAfterPurgeEnabled, DEFAULT_COMPACT_AFTER_PURGE_ENABLED);
        this.loadingLockStripes = loadingLockStripes;
        this.lmdbConfig = lmdbConfig;
        this.stagingLmdbConfig = stagingLmdbConfig;
        this.effectiveStreamCache = effectiveStreamCache;
        this.metaIdToRefStoreCache = metaIdToRefStoreCache;
    }

    @Min(0)
    @JsonPropertyDescription(
            "The maximum number of puts into the store (in a single load) before the " +
            "transaction is committed. There is only one write transaction available at a time so reducing " +
            "this value allows multiple loads to potentially each load a chunk at a time. However, load times " +
            "increase rapidly with values below around 2,000. For maximum performance of a single load set this " +
            "value to 0 to only commit at the very end of the load.")
    public int getMaxPutsBeforeCommit() {
        return maxPutsBeforeCommit;
    }

    @Min(0)
    @JsonPropertyDescription(
            "The maximum number of entries in one reference stream to purge before the " +
            "transaction is committed. A value high enough to purge all entries in one transaction is " +
            "preferable but for large reference streams this may result in errors due to the transaction " +
            "being too large.")
    public int getMaxPurgeDeletesBeforeCommit() {
        return maxPurgeDeletesBeforeCommit;
    }

    @JsonPropertyDescription(
            "The time to retain reference data for in the off heap store. The time is taken " +
            "from the time that the reference stream was last accessed, e.g. a lookup was made against it. " +
            "In ISO-8601 duration format, e.g. 'P1DT12H'. Used by job '" + RefDataPurge.JOB_NAME + "'.")
    public StroomDuration getPurgeAge() {
        return purgeAge;
    }

    @JsonPropertyDescription("TODO")
    public boolean isAutoPurgeEnabled() {
        return autoPurgeEnabled;
    }

    @JsonPropertyDescription(
            "If true a compaction process will be run after a successful purge to free " +
            "up disk space. If compaction is not run, space will be freed up inside the store for " +
            "future loads of that feed, but disk space will not be freed up. For compaction to " +
            "work, property lmdb.readerBlockedByWriter must also be set to true.")
    public boolean isCompactAfterPurgeEnabled() {
        return compactAfterPurgeEnabled;
    }

    @Min(2)
    @RequiresRestart(RestartScope.SYSTEM)
    @JsonPropertyDescription(
            "The number of lock stripes used for preventing multiple pipeline processes " +
            "from loading the same reference stream at the same time. Values should be a power of 2. " +
            "Lower values will mean it is more likely for two different streams from blocking one another.")
    public int getLoadingLockStripes() {
        return loadingLockStripes;
    }

    @JsonProperty("lmdb")
    public ReferenceDataLmdbConfig getLmdbConfig() {
        return lmdbConfig;
    }

    @JsonProperty("stagingLmdb")
    public ReferenceDataStagingLmdbConfig getStagingLmdbConfig() {
        return stagingLmdbConfig;
    }

    public CacheConfig getEffectiveStreamCache() {
        return effectiveStreamCache;
    }

    public CacheConfig getMetaIdToRefStoreCache() {
        return metaIdToRefStoreCache;
    }

    public ReferenceDataConfig withLmdbConfig(final ReferenceDataLmdbConfig lmdbConfig) {
        return new ReferenceDataConfig(
                maxPutsBeforeCommit,
                maxPurgeDeletesBeforeCommit,
                purgeAge,
                autoPurgeEnabled,
                compactAfterPurgeEnabled,
                loadingLockStripes,
                lmdbConfig,
                stagingLmdbConfig,
                effectiveStreamCache,
                metaIdToRefStoreCache);
    }

    public ReferenceDataConfig withPurgeAge(final StroomDuration purgeAge) {
        return new ReferenceDataConfig(
                maxPutsBeforeCommit,
                maxPurgeDeletesBeforeCommit,
                purgeAge,
                autoPurgeEnabled,
                compactAfterPurgeEnabled,
                loadingLockStripes,
                lmdbConfig,
                stagingLmdbConfig,
                effectiveStreamCache,
                metaIdToRefStoreCache);
    }

    public ReferenceDataConfig withMaxPutsBeforeCommit(final int maxPutsBeforeCommit) {
        return new ReferenceDataConfig(
                maxPutsBeforeCommit,
                maxPurgeDeletesBeforeCommit,
                purgeAge,
                autoPurgeEnabled,
                compactAfterPurgeEnabled,
                loadingLockStripes,
                lmdbConfig,
                stagingLmdbConfig,
                effectiveStreamCache,
                metaIdToRefStoreCache);
    }

    public ReferenceDataConfig withMaxPurgeDeletesBeforeCommit(final int maxPurgeDeletesBeforeCommit) {
        return new ReferenceDataConfig(
                maxPutsBeforeCommit,
                maxPurgeDeletesBeforeCommit,
                purgeAge,
                autoPurgeEnabled,
                compactAfterPurgeEnabled,
                loadingLockStripes,
                lmdbConfig,
                stagingLmdbConfig,
                effectiveStreamCache,
                metaIdToRefStoreCache);
    }

    public ReferenceDataConfig withEffectiveStreamCache(final CacheConfig effectiveStreamCache) {
        return new ReferenceDataConfig(
                maxPutsBeforeCommit,
                maxPurgeDeletesBeforeCommit,
                purgeAge,
                autoPurgeEnabled,
                compactAfterPurgeEnabled,
                loadingLockStripes,
                lmdbConfig,
                stagingLmdbConfig,
                effectiveStreamCache,
                metaIdToRefStoreCache);
    }

    @Override
    public String toString() {
        return "ReferenceDataConfig{" +
               "maxPutsBeforeCommit=" + maxPutsBeforeCommit +
               ", maxPurgeDeletesBeforeCommit=" + maxPurgeDeletesBeforeCommit +
               ", purgeAge=" + purgeAge +
               ", autoPurgeEnabled=" + autoPurgeEnabled +
               ", compactAfterPurgeEnabled=" + compactAfterPurgeEnabled +
               ", loadingLockStripes=" + loadingLockStripes +
               ", lmdbConfig=" + lmdbConfig +
               ", stagingLmdbConfig=" + stagingLmdbConfig +
               ", effectiveStreamCache=" + effectiveStreamCache +
               ", metaIdToRefStoreCache=" + metaIdToRefStoreCache +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder(new ReferenceDataConfig());
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private int maxPutsBeforeCommit;
        private int maxPurgeDeletesBeforeCommit;
        private StroomDuration purgeAge;
        private boolean autoPurgeEnabled;
        private boolean compactAfterPurgeEnabled;
        private int loadingLockStripes;
        private ReferenceDataLmdbConfig lmdbConfig;
        private ReferenceDataStagingLmdbConfig stagingLmdbConfig;
        private CacheConfig effectiveStreamCache;
        private CacheConfig metaIdToRefStoreCache;

        private Builder() {
        }

        public Builder(final ReferenceDataConfig referenceDataConfig) {
            this.maxPutsBeforeCommit = referenceDataConfig.maxPutsBeforeCommit;
            this.maxPurgeDeletesBeforeCommit = referenceDataConfig.maxPurgeDeletesBeforeCommit;
            this.purgeAge = referenceDataConfig.purgeAge;
            this.autoPurgeEnabled = referenceDataConfig.autoPurgeEnabled;
            this.compactAfterPurgeEnabled = referenceDataConfig.compactAfterPurgeEnabled;
            this.loadingLockStripes = referenceDataConfig.loadingLockStripes;
            this.lmdbConfig = referenceDataConfig.lmdbConfig;
            this.stagingLmdbConfig = referenceDataConfig.stagingLmdbConfig;
            this.effectiveStreamCache = referenceDataConfig.effectiveStreamCache;
            this.metaIdToRefStoreCache = referenceDataConfig.metaIdToRefStoreCache;
        }

        public static Builder builder(final ReferenceDataConfig source) {
            return new Builder(source);
        }

        public Builder withMaxPutsBeforeCommit(final int maxPutsBeforeCommit) {
            this.maxPutsBeforeCommit = maxPutsBeforeCommit;
            return this;
        }

        public Builder withMaxPurgeDeletesBeforeCommit(final int maxPurgeDeletesBeforeCommit) {
            this.maxPurgeDeletesBeforeCommit = maxPurgeDeletesBeforeCommit;
            return this;
        }

        public Builder withPurgeAge(final StroomDuration purgeAge) {
            this.purgeAge = purgeAge;
            return this;
        }

        public Builder withAutoPurgeEnabled(final boolean autoPurgeEnabled) {
            this.autoPurgeEnabled = autoPurgeEnabled;
            return this;
        }

        public Builder withCompactAfterPurgeEnabled(final boolean compactAfterPurgeEnabled) {
            this.compactAfterPurgeEnabled = compactAfterPurgeEnabled;
            return this;
        }

        public Builder withLoadingLockStripes(final int loadingLockStripes) {
            this.loadingLockStripes = loadingLockStripes;
            return this;
        }

        public Builder withLmdbConfig(final ReferenceDataLmdbConfig lmdbConfig) {
            this.lmdbConfig = lmdbConfig;
            return this;
        }

        public Builder withStagingLmdbConfig(final ReferenceDataStagingLmdbConfig stagingLmdbConfig) {
            this.stagingLmdbConfig = stagingLmdbConfig;
            return this;
        }

        public Builder withEffectiveStreamCache(final CacheConfig effectiveStreamCache) {
            this.effectiveStreamCache = effectiveStreamCache;
            return this;
        }

        public Builder withMetaIdToRefStoreCache(final CacheConfig metaIdToRefStoreCache) {
            this.metaIdToRefStoreCache = metaIdToRefStoreCache;
            return this;
        }

        public ReferenceDataConfig build() {
            return new ReferenceDataConfig(
                    maxPutsBeforeCommit,
                    maxPurgeDeletesBeforeCommit,
                    purgeAge,
                    autoPurgeEnabled,
                    compactAfterPurgeEnabled,
                    loadingLockStripes,
                    lmdbConfig,
                    stagingLmdbConfig,
                    effectiveStreamCache,
                    metaIdToRefStoreCache);
        }
    }
}
