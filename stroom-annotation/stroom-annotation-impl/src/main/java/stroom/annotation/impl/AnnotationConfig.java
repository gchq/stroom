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

package stroom.annotation.impl;

import stroom.config.common.AbstractDbConfig;
import stroom.config.common.ConnectionConfig;
import stroom.config.common.ConnectionPoolConfig;
import stroom.config.common.HasDbConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.BootStrapConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class AnnotationConfig extends AbstractConfig implements IsStroomConfig, HasDbConfig {

    public static final String DEFAULT_RETENTION_PERIOD = "5y";

    private final AnnotationDBConfig dbConfig;
    private final List<String> standardComments;
    private final String createText;
    private final String defaultRetentionPeriod;
    private final StroomDuration physicalDeleteAge;
    private final CacheConfig annotationTagCache;
    private final CacheConfig annotationFeedCache;

    public AnnotationConfig() {
        dbConfig = new AnnotationDBConfig();
        standardComments = new ArrayList<>();
        createText = "Create Annotation";
        defaultRetentionPeriod = DEFAULT_RETENTION_PERIOD;
        physicalDeleteAge = StroomDuration.ofDays(7);
        annotationTagCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        annotationFeedCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public AnnotationConfig(@JsonProperty("db") final AnnotationDBConfig dbConfig,
                            @JsonProperty("standardComments") final List<String> standardComments,
                            @JsonProperty("createText") final String createText,
                            @JsonProperty("defaultRetentionPeriod") final String defaultRetentionPeriod,
                            @JsonProperty("physicalDeleteAge") final StroomDuration physicalDeleteAge,
                            @JsonProperty("annotationTagCache") final CacheConfig annotationTagCache,
                            @JsonProperty("annotationFeedCache") final CacheConfig annotationFeedCache) {
        this.dbConfig = dbConfig;
        this.standardComments = standardComments;
        this.createText = createText;
        this.defaultRetentionPeriod = defaultRetentionPeriod;
        this.physicalDeleteAge = physicalDeleteAge;
        this.annotationTagCache = annotationTagCache;
        this.annotationFeedCache = annotationFeedCache;
    }

    @Override
    @JsonProperty("db")
    public AnnotationDBConfig getDbConfig() {
        return dbConfig;
    }

    @JsonProperty("standardComments")
    @JsonPropertyDescription("A list of standard comments that can be added to annotations")
    public List<String> getStandardComments() {
        return standardComments;
    }

    @JsonProperty("createText")
    @JsonPropertyDescription("The text to display to create an annotation")
    public String getCreateText() {
        return createText;
    }

    @JsonProperty("defaultRetentionPeriod")
    @JsonPropertyDescription("How long should we retain annotations by default, e.g. 5y")
    public String getDefaultRetentionPeriod() {
        return defaultRetentionPeriod;
    }

    @JsonPropertyDescription("How long to keep logically deleted annotations before deleting them. " +
                             "In ISO-8601 duration format, e.g. 'P1DT12H'")
    public StroomDuration getPhysicalDeleteAge() {
        return physicalDeleteAge;
    }

    @JsonPropertyDescription("Cache config for annotation tags")
    public CacheConfig getAnnotationTagCache() {
        return annotationTagCache;
    }

    @JsonPropertyDescription("Cache config for annotation feed reference")
    public CacheConfig getAnnotationFeedCache() {
        return annotationFeedCache;
    }

    @BootStrapConfig
    public static class AnnotationDBConfig extends AbstractDbConfig {

        public AnnotationDBConfig() {
            super();
        }

        @JsonCreator
        public AnnotationDBConfig(
                @JsonProperty(PROP_NAME_CONNECTION) final ConnectionConfig connectionConfig,
                @JsonProperty(PROP_NAME_CONNECTION_POOL) final ConnectionPoolConfig connectionPoolConfig) {
            super(connectionConfig, connectionPoolConfig);
        }
    }
}
