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

package stroom.event.logging.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder(alphabetic = true)
public class LoggingConfig extends AbstractConfig implements IsStroomConfig {

    private final boolean logEveryRestCallEnabled;

    private final boolean omitRecordDetailsLoggingEnabled;

    private final int maxListElements;

    private final int maxDataElementStringLength;

    private final CacheConfig deviceCache;

    public LoggingConfig() {
        logEveryRestCallEnabled = false;
        omitRecordDetailsLoggingEnabled = true;
        maxListElements = 5;
        maxDataElementStringLength = 500;
        deviceCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterWrite(StroomDuration.ofMinutes(60))
                .build();
    }

    @JsonCreator
    public LoggingConfig(@JsonProperty("logEveryRestCallEnabled") final boolean logEveryRestCallEnabled,
                         @JsonProperty("omitRecordDetailsLoggingEnabled") final boolean omitRecordDetailsLoggingEnabled,
                         @JsonProperty("maxListElements") final int maxListElements,
                         @JsonProperty("maxDataElementStringLength") final int maxDataElementStringLength,
                         @JsonProperty("deviceCache") final CacheConfig deviceCache) {
        this.logEveryRestCallEnabled = logEveryRestCallEnabled;
        this.omitRecordDetailsLoggingEnabled = omitRecordDetailsLoggingEnabled;
        this.maxListElements = maxListElements;
        this.maxDataElementStringLength = maxDataElementStringLength;
        this.deviceCache = deviceCache;
    }

    @JsonProperty("omitRecordDetailsLoggingEnabled")
    @JsonPropertyDescription("Suppress standard database record fields " +
            "'createUser', 'updateUser', 'createTime', 'updateTime' and 'version' being reported within event log.")
    public boolean isOmitRecordDetailsLoggingEnabled() {
        return omitRecordDetailsLoggingEnabled;
    }

    @JsonProperty("maxListElements")
    @JsonPropertyDescription("Maximum number of elements in event log before truncation.")
    public int getMaxListElements() {
        return maxListElements;
    }

    @JsonProperty("maxDataElementStringLength")
    @JsonPropertyDescription("Maximum number of characters for values of Data items.")
    public int getMaxDataElementStringLength() {
        return maxDataElementStringLength;
    }

    @JsonProperty("logEveryRestCallEnabled")
    @JsonPropertyDescription("Ensure that every RESTful service calls is logged, not only user initiated ones.")
    public boolean isLogEveryRestCallEnabled() {
        return logEveryRestCallEnabled;
    }

    @JsonProperty
    @JsonPropertyDescription("The cache configuration for remembering device objects for IP addresses.")
    public CacheConfig getDeviceCache() {
        return deviceCache;
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
