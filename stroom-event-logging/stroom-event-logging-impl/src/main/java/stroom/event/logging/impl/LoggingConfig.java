/*
 * Copyright 2020 Crown Copyright
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
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.Objects;
import javax.inject.Singleton;


@JsonInclude(Include.NON_NULL)
@Singleton
public class LoggingConfig extends AbstractConfig {

    public static final Boolean LOG_EVERY_REST_CALL_ENABLED_DEFAULT = Boolean.FALSE;
    public static final Boolean OMIT_RECORD_DETAILS_LOGGING_ENABLED_DEFAULT = Boolean.TRUE;
    public static final Integer MAX_LIST_ELEMENTS_DEFAULT = 5;
    public static final Integer MAX_DATA_ELEMENT_STRING_LENGTH_DEFAULT = 500;
    public static final CacheConfig DEVICE_CACHE_DEFAULT = CacheConfig.builder()
            .maximumSize(1000L)
            .expireAfterWrite(StroomDuration.ofMinutes(60))
            .build();

    private Boolean logEveryRestCallEnabled = LOG_EVERY_REST_CALL_ENABLED_DEFAULT;

    private Boolean omitRecordDetailsLoggingEnabled = OMIT_RECORD_DETAILS_LOGGING_ENABLED_DEFAULT;

    private Integer maxListElements = MAX_LIST_ELEMENTS_DEFAULT;

    private Integer maxDataElementStringLength = MAX_DATA_ELEMENT_STRING_LENGTH_DEFAULT;

    private CacheConfig deviceCache = DEVICE_CACHE_DEFAULT;

    public LoggingConfig() {
    }

    @JsonCreator
    public LoggingConfig(@JsonProperty("logEveryRestCallEnabled") final Boolean logEveryRestCallEnabled,
                         @JsonProperty("omitRecordDetailsLoggingEnabled") final Boolean omitRecordDetailsLoggingEnabled,
                         @JsonProperty("maxListElements") final Integer maxListElements,
                         @JsonProperty("maxDataElementStringLength") final Integer maxDataElementStringLength,
                         @JsonProperty("deviceCache") final CacheConfig deviceCache) {
        this.logEveryRestCallEnabled = Objects.requireNonNullElse(logEveryRestCallEnabled,
                LOG_EVERY_REST_CALL_ENABLED_DEFAULT);
        this.omitRecordDetailsLoggingEnabled = Objects.requireNonNullElse(omitRecordDetailsLoggingEnabled,
                OMIT_RECORD_DETAILS_LOGGING_ENABLED_DEFAULT);
        this.maxListElements = Objects.requireNonNullElse(maxListElements, MAX_LIST_ELEMENTS_DEFAULT);
        this.maxDataElementStringLength = Objects.requireNonNullElse(
                maxDataElementStringLength,
                MAX_DATA_ELEMENT_STRING_LENGTH_DEFAULT);
        this.deviceCache = CacheConfig.withDefaults(deviceCache, DEVICE_CACHE_DEFAULT);
    }

    @JsonProperty("omitRecordDetailsLoggingEnabled")
    @JsonPropertyDescription("Suppress standard database record fields " +
            "'createUser', 'updateUser', 'createTime', 'updateTime' and 'version' being reported within event log.")
    public boolean isOmitRecordDetailsLoggingEnabled() {
//        return omitRecordDetailsLoggingEnabled;
        return Objects.requireNonNullElse(
                omitRecordDetailsLoggingEnabled,
                OMIT_RECORD_DETAILS_LOGGING_ENABLED_DEFAULT);
    }

    public void setOmitRecordDetailsLoggingEnabled(final Boolean omitRecordDetailsLoggingEnabled) {
//        this.omitRecordDetailsLoggingEnabled = omitRecordDetailsLoggingEnabled;
        this.omitRecordDetailsLoggingEnabled = Objects.requireNonNullElse(
                omitRecordDetailsLoggingEnabled,
                OMIT_RECORD_DETAILS_LOGGING_ENABLED_DEFAULT);
    }

    @JsonProperty("maxListElements")
    @JsonPropertyDescription("Maximum number of elements in event log before truncation.")
    public int getMaxListElements() {
        return Objects.requireNonNullElse(maxListElements, MAX_LIST_ELEMENTS_DEFAULT);
    }

    public void setMaxListElements(final Integer maxListElements) {
        this.maxListElements = Objects.requireNonNullElse(maxListElements, MAX_LIST_ELEMENTS_DEFAULT);
    }

    @JsonProperty("maxDataElementStringLength")
    @JsonPropertyDescription("Maximum number of characters for values of Data items.")
    public int getMaxDataElementStringLength() {
        return Objects.requireNonNullElse(maxDataElementStringLength, MAX_DATA_ELEMENT_STRING_LENGTH_DEFAULT);
    }

    public void setMaxDataElementStringLength(final Integer maxDataElementStringLength) {
        this.maxDataElementStringLength = Objects.requireNonNullElse(
                maxDataElementStringLength,
                MAX_DATA_ELEMENT_STRING_LENGTH_DEFAULT);
    }

    @JsonProperty("logEveryRestCallEnabled")
    @JsonPropertyDescription("Ensure that every RESTful service calls is logged, not only user initiated ones.")
    public boolean isLogEveryRestCallEnabled() {
//        return this.logEveryRestCallEnabled;
        return Objects.requireNonNullElse(logEveryRestCallEnabled, LOG_EVERY_REST_CALL_ENABLED_DEFAULT);
    }

    public void setLogEveryRestCallEnabled(final Boolean logEveryRestCallEnabled) {
//        this.logEveryRestCallEnabled = logEveryRestCallEnabled;
        this.logEveryRestCallEnabled = Objects.requireNonNullElse(
                logEveryRestCallEnabled,
                LOG_EVERY_REST_CALL_ENABLED_DEFAULT);
    }

    @JsonProperty
    @JsonPropertyDescription("The cache configuration for remembering device objects for IP addresses.")
    public CacheConfig getDeviceCache() {
        return Objects.requireNonNullElse(deviceCache, DEVICE_CACHE_DEFAULT);
    }

    public void setDeviceCache(final CacheConfig deviceCache) {
        this.deviceCache = CacheConfig.withDefaults(deviceCache, DEVICE_CACHE_DEFAULT);
    }

    @Override
    public String toString() {
        return "LoggingConfig{" +
                "logEveryRestCallEnabled=" + logEveryRestCallEnabled +
                ", omitRecordDetailsLoggingEnabled=" + omitRecordDetailsLoggingEnabled +
                ", maxListElements=" + maxListElements +
                ", maxDataElementStringLength=" + maxDataElementStringLength +
                ", deviceCache=" + deviceCache +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final LoggingConfig that = (LoggingConfig) o;
        return Objects.equals(logEveryRestCallEnabled, that.logEveryRestCallEnabled) && Objects.equals(
                omitRecordDetailsLoggingEnabled,
                that.omitRecordDetailsLoggingEnabled) && Objects.equals(maxListElements,
                that.maxListElements) && Objects.equals(maxDataElementStringLength,
                that.maxDataElementStringLength) && Objects.equals(deviceCache, that.deviceCache);
    }

    @Override
    public int hashCode() {
        return Objects.hash(logEveryRestCallEnabled,
                omitRecordDetailsLoggingEnabled,
                maxListElements,
                maxDataElementStringLength,
                deviceCache);
    }
}
