/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app.handler;

import stroom.receive.common.FeedStatusResourceV2;
import stroom.util.cache.CacheConfig;
import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.ResourcePaths;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotNull;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
public class FeedStatusConfig extends AbstractConfig implements IsProxyConfig {

    public static final String PROP_NAME_URL = "url";
    public static final String DEFAULT_URL_PATH = ResourcePaths.buildAuthenticatedApiPath(
            FeedStatusResourceV2.BASE_RESOURCE_PATH,
            FeedStatusResourceV2.GET_FEED_STATUS_PATH_PART);

    @JsonProperty(PROP_NAME_URL)
    @JsonPropertyDescription("The remote URL to fetch feed status from if enabled. If not set the default " +
                             "path will be combined with the downstreamHost.")
    private final String feedStatusUrl;

    @RequiresProxyRestart
    @NotNull
    @JsonProperty("feedStatusCache")
    @JsonPropertyDescription("Configure caching of the fetched feed status.")
    private final CacheConfig feedStatusCache;

    public FeedStatusConfig() {
        feedStatusUrl = null;
        feedStatusCache = buildDefaultCacheConfig();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public FeedStatusConfig(@JsonProperty(PROP_NAME_URL) final String feedStatusUrl,
                            @JsonProperty("feedStatusCache") final CacheConfig feedStatusCache) {
        this.feedStatusUrl = feedStatusUrl;
        this.feedStatusCache = Objects.requireNonNullElseGet(
                feedStatusCache,
                FeedStatusConfig::buildDefaultCacheConfig);
    }

    private static CacheConfig buildDefaultCacheConfig() {
        return CacheConfig
                .builder()
                .maximumSize(1_000L)
                .statisticsMode(CacheConfig.PROXY_DEFAULT_STATISTICS_MODE)
                .build();
    }

    @JsonPropertyDescription(
            "The full url for the remote stroom/proxy that will perform the feed status checks. " +
            "If the remote is a proxy, it must also be configured to use feed status checks " +
            "so that it can obtain them from another stroom/proxy downstream to it. " +
            "If not set the downstreamHost configuration will be combined with the default API " +
            "path. Only set this property if you wish to use a non-default path " +
            "or you want to use a different host/port/scheme to that defined in downstreamHost.")
    public String getFeedStatusUrl() {
        return feedStatusUrl;
    }

    public CacheConfig getFeedStatusCache() {
        return feedStatusCache;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final FeedStatusConfig that = (FeedStatusConfig) o;
        return
                Objects.equals(feedStatusUrl, that.feedStatusUrl) &&
                Objects.equals(feedStatusCache, that.feedStatusCache);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                feedStatusUrl,
                feedStatusCache);
    }

    @Override
    public String toString() {
        return "FeedStatusConfig{" +
               ", feedStatusUrl='" + feedStatusUrl + '\'' +
               ", feedStatusCache=" + feedStatusCache +
               '}';
    }
}
