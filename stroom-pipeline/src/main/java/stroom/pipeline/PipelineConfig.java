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

package stroom.pipeline;

import stroom.pipeline.destination.AppenderConfig;
import stroom.pipeline.filter.XmlSchemaConfig;
import stroom.pipeline.filter.XsltConfig;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;
import stroom.util.xml.ParserConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class PipelineConfig extends AbstractConfig implements IsStroomConfig {

    private final AppenderConfig appenderConfig;
    private final ParserConfig parserConfig;
    private final ReferenceDataConfig referenceDataConfig;
    private final XmlSchemaConfig xmlSchemaConfig;
    private final XsltConfig xsltConfig;
    private final CacheConfig httpClientCache;
    private final CacheConfig pipelineDataCache;
    private final CacheConfig documentPermissionCache;

    public PipelineConfig() {
        appenderConfig = new AppenderConfig();
        parserConfig = new ParserConfig();
        referenceDataConfig = new ReferenceDataConfig();
        xmlSchemaConfig = new XmlSchemaConfig();
        xsltConfig = new XsltConfig();
        httpClientCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        pipelineDataCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
                .build();
        // The benefit of this cache is limited as it relies on various other caches below it.
        // We have no change handlers due to the complexity of the number of things that can affect this
        // cache, so keep the time short and expire after write, not access.
        documentPermissionCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterWrite(StroomDuration.ofSeconds(30))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public PipelineConfig(@JsonProperty("appender") final AppenderConfig appenderConfig,
                          @JsonProperty("parser") final ParserConfig parserConfig,
                          @JsonProperty("referenceData") final ReferenceDataConfig referenceDataConfig,
                          @JsonProperty("xmlSchema") final XmlSchemaConfig xmlSchemaConfig,
                          @JsonProperty("xslt") final XsltConfig xsltConfig,
                          @JsonProperty("httpClientCache") final CacheConfig httpClientCache,
                          @JsonProperty("pipelineDataCache") final CacheConfig pipelineDataCache,
                          @JsonProperty("documentPermissionCache") final CacheConfig documentPermissionCache) {
        this.appenderConfig = appenderConfig;
        this.parserConfig = parserConfig;
        this.referenceDataConfig = referenceDataConfig;
        this.xmlSchemaConfig = xmlSchemaConfig;
        this.xsltConfig = xsltConfig;
        this.httpClientCache = httpClientCache;
        this.pipelineDataCache = pipelineDataCache;
        this.documentPermissionCache = documentPermissionCache;
    }

    @JsonProperty("appender")
    public AppenderConfig getAppenderConfig() {
        return appenderConfig;
    }

    @JsonProperty("parser")
    public ParserConfig getParserConfig() {
        return parserConfig;
    }

    @JsonProperty("referenceData")
    public ReferenceDataConfig getReferenceDataConfig() {
        return referenceDataConfig;
    }

    @JsonProperty("xmlSchema")
    public XmlSchemaConfig getXmlSchemaConfig() {
        return xmlSchemaConfig;
    }

    @JsonProperty("xslt")
    public XsltConfig getXsltConfig() {
        return xsltConfig;
    }

    public CacheConfig getHttpClientCache() {
        return httpClientCache;
    }

    public CacheConfig getPipelineDataCache() {
        return pipelineDataCache;
    }

    public CacheConfig getDocumentPermissionCache() {
        return documentPermissionCache;
    }
}
