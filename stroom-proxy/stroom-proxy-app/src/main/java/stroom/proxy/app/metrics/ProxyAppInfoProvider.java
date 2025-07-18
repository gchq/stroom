package stroom.proxy.app.metrics;

import stroom.dropwizard.common.prometheus.AbstractAppInfoProvider;
import stroom.proxy.app.handler.ProxyId;

import jakarta.inject.Inject;

import java.util.Map;

public class ProxyAppInfoProvider extends AbstractAppInfoProvider {

    public static final String PROXY_ID_KEY = "proxy_id";
    private final ProxyId proxyId;

    @Inject
    public ProxyAppInfoProvider(final ProxyId proxyId) {
        this.proxyId = proxyId;
    }

    @Override
    protected Map<String, String> getAdditionalAppInfo() {
        return Map.of(PROXY_ID_KEY, proxyId.getId());
    }

    @Override
    public Map<String, String> getNodeLabels() {
        return Map.of(PROXY_ID_KEY, proxyId.getId());
    }
}
