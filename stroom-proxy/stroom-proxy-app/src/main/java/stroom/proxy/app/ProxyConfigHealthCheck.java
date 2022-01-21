package stroom.proxy.app;

import stroom.util.HasHealthCheck;
import stroom.util.HealthCheckUtils;

import com.codahale.metrics.health.HealthCheck.Result;

import java.util.Map;
import javax.inject.Inject;
import javax.inject.Provider;

// TODO should not be a healthcheck, should be HasSystemInfo if we can get it to
//   work on the admin port as there is no auth in proxy
public class ProxyConfigHealthCheck implements HasHealthCheck {

    private final Provider<ProxyConfig> proxyConfigProvider;

    @Inject
    ProxyConfigHealthCheck(final Provider<ProxyConfig> proxyConfigProvider) {
        this.proxyConfigProvider = proxyConfigProvider;
    }

    @Override
    public Result getHealth() {
        Map<String, Object> detailMap = HealthCheckUtils.beanToMap(proxyConfigProvider.get());

        // We don't really want passwords appearing on the admin page so mask them out.
        HealthCheckUtils.maskPasswords(detailMap);

        return Result.builder()
                .healthy()
                .withDetail("values", detailMap)
                .build();
    }
}
