package stroom.proxy.app;

import stroom.util.config.annotations.ReadOnly;
import stroom.util.io.PathConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import javax.inject.Singleton;

@Singleton
@JsonPropertyOrder(alphabetic = true)
public class ProxyPathConfig extends PathConfig implements IsProxyConfig {

    public ProxyPathConfig() {
    }

    @JsonCreator
    public ProxyPathConfig(@JsonProperty("home") final String home,
                           @JsonProperty("temp") final String temp) {
        super(home, temp);
    }

    /**
     * Will be created on boot in App
     */
    @Override
    @ReadOnly
    @JsonPropertyDescription("By default, unless configured otherwise, all other configured paths " +
            "(except proxyConfig.path.temp) will be relative to this directory. If this value is null then" +
            "Stroom-Proxy will use either of the following to derive proxyConfig.path.home: the directory of the " +
            "Stroom-proxy application JAR file or ~/.stroom-proxy. " +
            "It must be an absolute path and it does not support '~' or variable substitution like other paths.")
    public String getHome() {
        return super.getHome();
    }

    /**
     * Will be created on boot in App
     */
    @Override
    @ReadOnly
    @JsonPropertyDescription("This directory is used by stroom-proxy to write any temporary file to. " +
            "Should only be set per node in application YAML configuration file. " +
            "If not set then Stroom-Proxy will use <SYSTEM TEMP>/stroom-proxy." +
            "It must be an absolute path and it does not support '~' or variable substitution like other paths.")
    public String getTemp() {
        return super.getTemp();
    }

    public ProxyPathConfig withHome(final String home) {
        return new ProxyPathConfig(home, getTemp());
    }

    public ProxyPathConfig withTemp(final String temp) {
        return new ProxyPathConfig(getHome(), temp);
    }
}
