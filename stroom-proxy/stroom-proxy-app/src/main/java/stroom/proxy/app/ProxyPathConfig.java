package stroom.proxy.app;

import stroom.util.config.annotations.ReadOnly;
import stroom.util.io.PathConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.annotation.Nonnull;
import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class ProxyPathConfig extends PathConfig implements IsProxyConfig {

    @Override
    @ReadOnly
    @JsonPropertyDescription("By default, unless configured otherwise, all other configured paths " +
            "(except proxyConfig.path.temp) will be relative to this directory. If this value is null then" +
            "Stroom-Proxy will use either of the following to derive proxyConfig.path.home: the directory of the " +
            "Stroom-proxy application JAR file or ~/.stroom-proxy. Should only be set per node in application " +
            "YAML configuration file")
    public String getHome() {
        return super.getHome();
    }

    @Override
    @Nonnull
    @ReadOnly
    @JsonPropertyDescription("This directory is used by stroom-proxy to write any temporary file to. " +
            "Should only be set per node in application YAML configuration file.")
    public String getTemp() {
        return super.getTemp();
    }
}
