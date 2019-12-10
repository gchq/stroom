package stroom.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.pipeline.destination.AppenderConfig;
import stroom.pipeline.filter.XmlSchemaConfig;
import stroom.pipeline.filter.XsltConfig;
import stroom.pipeline.refdata.store.RefDataStoreConfig;
import stroom.util.shared.IsConfig;
import stroom.util.xml.ParserConfig;

import javax.inject.Singleton;

@Singleton
public class PipelineConfig implements IsConfig {
    private AppenderConfig appenderConfig = new AppenderConfig();
    private ParserConfig parserConfig = new ParserConfig();
    private RefDataStoreConfig refDataStoreConfig = new RefDataStoreConfig();
    private XmlSchemaConfig xmlSchemaConfig = new XmlSchemaConfig();
    private XsltConfig xsltConfig = new XsltConfig();

    @JsonProperty("appender")
    public AppenderConfig getAppenderConfig() {
        return appenderConfig;
    }

    public void setAppenderConfig(final AppenderConfig appenderConfig) {
        this.appenderConfig = appenderConfig;
    }

    @JsonProperty("parser")
    public ParserConfig getParserConfig() {
        return parserConfig;
    }

    public void setParserConfig(final ParserConfig parserConfig) {
        this.parserConfig = parserConfig;
    }

    @JsonProperty("referenceData")
    public RefDataStoreConfig getRefDataStoreConfig() {
        return refDataStoreConfig;
    }

    public void setRefDataStoreConfig(final RefDataStoreConfig refDataStoreConfig) {
        this.refDataStoreConfig = refDataStoreConfig;
    }

    @JsonProperty("xmlSchema")
    public XmlSchemaConfig getXmlSchemaConfig() {
        return xmlSchemaConfig;
    }

    public void setXmlSchemaConfig(final XmlSchemaConfig xmlSchemaConfig) {
        this.xmlSchemaConfig = xmlSchemaConfig;
    }

    @JsonProperty("xslt")
    public XsltConfig getXsltConfig() {
        return xsltConfig;
    }

    public void setXsltConfig(final XsltConfig xsltConfig) {
        this.xsltConfig = xsltConfig;
    }
}
