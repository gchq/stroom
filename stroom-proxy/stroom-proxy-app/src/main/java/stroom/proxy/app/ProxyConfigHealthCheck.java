package stroom.proxy.app;

import com.codahale.metrics.health.HealthCheck.Result;
import stroom.util.HasHealthCheck;
import stroom.util.HealthCheckUtils;

import javax.inject.Inject;
import java.util.Map;

public class ProxyConfigHealthCheck implements HasHealthCheck {
    private final ProxyConfig proxyConfig;

    @Inject
    ProxyConfigHealthCheck(final ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    @Override
    public Result getHealth() {
        Map<String, Object> detailMap = HealthCheckUtils.beanToMap(proxyConfig);
        return Result.builder()
                .healthy()
                .withDetail("values", detailMap)
                .build();
    }
}
