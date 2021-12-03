package stroom.pipeline;

import stroom.pipeline.destination.AppenderConfig;
import stroom.pipeline.filter.XmlSchemaConfig;
import stroom.pipeline.filter.XsltConfig;
import stroom.pipeline.refdata.ReferenceDataConfig;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;
import stroom.util.xml.ParserConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;


public class PipelineConfig extends AbstractConfig {

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
        documentPermissionCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofMinutes(10))
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
