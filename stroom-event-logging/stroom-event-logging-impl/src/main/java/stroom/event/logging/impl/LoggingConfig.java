/*
 * Copyright 2020 Crown Copyright
 *
 *
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * See the License for the specific language governing permissions and
 * Unless required by applicable law or agreed to in writing, software
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * You may obtain a copy of the License at
 * distributed under the License is distributed on an "AS IS" BASIS,
 * import stroom.util.shared.IsStroomConfig;
 * limitations under the License.
 * you may not use this file except in compliance with the License.
 */

package stroom.event.logging.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;


@Singleton
public class LoggingConfig extends AbstractConfig implements IsStroomConfig {

    private boolean logEveryRestCallEnabled = false;

    private boolean omitRecordDetailsLoggingEnabled = true;

    private int maxListElements = 5;

    private int maxDataElementStringLength = 500;

    private CacheConfig deviceCache = CacheConfig.builder()
            .maximumSize(1000L)
            .expireAfterWrite(StroomDuration.ofMinutes(60))
            .build();

    @JsonProperty("omitRecordDetailsLoggingEnabled")
    @JsonPropertyDescription("Suppress standard database record fields " +
            "'createUser', 'updateUser', 'createTime', 'updateTime' and 'version' being reported within event log.")
    public boolean isOmitRecordDetailsLoggingEnabled() {
        return omitRecordDetailsLoggingEnabled;
    }

    public void setOmitRecordDetailsLoggingEnabled(final boolean omitRecordHistoryEnabled) {
        this.omitRecordDetailsLoggingEnabled = omitRecordHistoryEnabled;
    }

    @JsonProperty("maxListElements")
    @JsonPropertyDescription("Maximum number of elements in event log before truncation.")
    public int getMaxListElements() {
        return maxListElements;
    }

    public void setMaxListElements(final int maxListElements) {
        this.maxListElements = maxListElements;
    }

    @JsonProperty("maxDataElementStringLength")
    @JsonPropertyDescription("Maximum number of characters for values of Data items.")
    public int getMaxDataElementStringLength() {
        return maxDataElementStringLength;
    }

    public void setMaxDataElementStringLength(final int maxDataElementStringLength) {
        this.maxDataElementStringLength = maxDataElementStringLength;
    }

    @JsonProperty("logEveryRestCallEnabled")
    @JsonPropertyDescription("Ensure that every RESTful service calls is logged, not only user initiated ones.")
    public boolean isLogEveryRestCallEnabled() {
        return logEveryRestCallEnabled;
    }

    public void setLogEveryRestCallEnabled(final boolean logEveryRestCallEnabled) {
        this.logEveryRestCallEnabled = logEveryRestCallEnabled;
    }

    @JsonProperty
    @JsonPropertyDescription("The cache configuration for remembering device objects for IP addresses.")
    public CacheConfig getDeviceCache() {
        return deviceCache;
    }

    public void setDeviceCache(final CacheConfig deviceCache) {
        this.deviceCache = deviceCache;
    }

    @Override
    public String toString() {
        return "LoggingConfig{" +
                "maxListElements=" + maxListElements +
                ", " +
                "logEveryRestCallEnabled=" + logEveryRestCallEnabled +
                '}';
    }

}
