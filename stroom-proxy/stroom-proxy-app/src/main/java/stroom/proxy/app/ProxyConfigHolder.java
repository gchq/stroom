package stroom.proxy.app;

import java.nio.file.Path;

public class ProxyConfigHolder {

    private final ProxyConfig proxyConfig;
    private final Path configFile;

    public ProxyConfigHolder(final ProxyConfig proxyConfig,
                             final Path configFile) {
        this.proxyConfig = proxyConfig;
        this.configFile = configFile;
    }

    public ProxyConfig getProxyConfig() {
        return proxyConfig;
    }

    public Path getConfigFile() {
        return configFile;
    }
}
