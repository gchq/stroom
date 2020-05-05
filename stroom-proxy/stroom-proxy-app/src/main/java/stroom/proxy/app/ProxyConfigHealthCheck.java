package stroom.proxy.app;

import stroom.util.HasHealthCheck;
import stroom.util.HealthCheckUtils;

import com.codahale.metrics.health.HealthCheck.Result;

import javax.inject.Inject;
import java.util.Map;

// TODO should not be a healthcheck, should be HasSystemInfo if we can get it to
//   work on the admin port as there is no auth in proxy
public class ProxyConfigHealthCheck implements HasHealthCheck {
    private final ProxyConfig proxyConfig;

    @Inject
    ProxyConfigHealthCheck(final ProxyConfig proxyConfig) {
        this.proxyConfig = proxyConfig;
    }

    @Override
    public Result getHealth() {
        Map<String, Object> detailMap = HealthCheckUtils.beanToMap(proxyConfig);

        // We don't really want passwords appearing on the admin page so mask them out.
        HealthCheckUtils.maskPasswords(detailMap);

        return Result.builder()
                .healthy()
                .withDetail("values", detailMap)
                .build();
    }
}
