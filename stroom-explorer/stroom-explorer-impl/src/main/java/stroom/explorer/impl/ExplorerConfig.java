package stroom.explorer.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.StandardExplorerTags;
import stroom.util.NullSafe;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.validation.AllMatchPattern;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


@JsonPropertyOrder(alphabetic = true)
public class ExplorerConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    private static final Pattern NODE_TAG_PATTERN = Pattern.compile(ExplorerNode.TAG_PATTERN_STR);

    private final ExplorerDbConfig dbConfig;
    private final CacheConfig docRefInfoCache;
    private final CacheConfig brokenDependenciesCache;
    private final Set<String> suggestedTags;

    public ExplorerConfig() {
        dbConfig = new ExplorerDbConfig();
        docRefInfoCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        brokenDependenciesCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterWrite(StroomDuration.ofMinutes(1))
                .build();
        suggestedTags = Arrays.stream(StandardExplorerTags.values())
                .map(StandardExplorerTags::getTagName)
                .collect(Collectors.toSet());
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public ExplorerConfig(@JsonProperty("db") final ExplorerDbConfig dbConfig,
                          @JsonProperty("docRefInfoCache") final CacheConfig docRefInfoCache,
                          @JsonProperty("brokenDependenciesCache") final CacheConfig brokenDependenciesCache,
                          @JsonProperty("suggestedTags") final Set<String> suggestedTags) {
        this.dbConfig = dbConfig;
        this.docRefInfoCache = docRefInfoCache;
        this.brokenDependenciesCache = brokenDependenciesCache;
        // Filter out any blanks
        this.suggestedTags = NullSafe.stream(suggestedTags)
                .filter(tag -> !NullSafe.isBlankString(tag))
                .collect(Collectors.toSet());
    }

    @Override
    @JsonProperty("db")
    public ExplorerDbConfig getDbConfig() {
        return dbConfig;
    }


    @JsonProperty("docRefInfoCache")
    public CacheConfig getDocRefInfoCache() {
        return docRefInfoCache;
    }

    @JsonProperty("brokenDependenciesCache")
    public CacheConfig getBrokenDependenciesCache() {
        return brokenDependenciesCache;
    }

    @AllMatchPattern(pattern = ExplorerNode.TAG_PATTERN_STR)
    @JsonPropertyDescription(
            "A set of explorer node tags that will be provided to the user to pick from " +
                    "along with any custom tags added to nodes by the user.")
    @JsonProperty("suggestedTags")
    public Set<String> getSuggestedTags() {
        return Objects.requireNonNullElseGet(suggestedTags, Collections::emptySet);
    }


    // --------------------------------------------------------------------------------


    @BootStrapConfig
    public static class ExplorerDbConfig extends AbstractDbConfig {

        public ExplorerDbConfig() {
            super();
        }

        @SuppressWarnings("unused")
        @JsonCreator
        public ExplorerDbConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
