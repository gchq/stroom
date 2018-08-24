package stroom.pipeline;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.pipeline.destination.AppenderConfig;
import stroom.pipeline.filter.XsltConfig;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class PipelineConfig {
    private XsltConfig xsltConfig;
    private AppenderConfig appenderConfig;

    public PipelineConfig() {
        xsltConfig = new XsltConfig();
        appenderConfig = new AppenderConfig();
    }

    @Inject
    public PipelineConfig(final XsltConfig xsltConfig,
                          final AppenderConfig appenderConfig) {
        this.xsltConfig = xsltConfig;
        this.appenderConfig = appenderConfig;
    }

    @JsonProperty("xslt")
    public XsltConfig getXsltConfig() {
        return xsltConfig;
    }

    public void setXsltConfig(final XsltConfig xsltConfig) {
        this.xsltConfig = xsltConfig;
    }

    @JsonProperty("appender")
    public AppenderConfig getAppenderConfig() {
        return appenderConfig;
    }

    public void setAppenderConfig(final AppenderConfig appenderConfig) {
        this.appenderConfig = appenderConfig;
    }
}
