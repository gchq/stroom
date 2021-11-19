package stroom.config.global.impl;

import stroom.config.app.AppConfig;
import stroom.util.HealthCheckUtils;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;

import java.util.Map;
import javax.inject.Inject;

/**
 * Allows us to see the {@link AppConfig} tree on a node
 */
public class AppConfigSystemInfo implements HasSystemInfo {

    private final AppConfig appConfig;

    @Inject
    public AppConfigSystemInfo(final AppConfig appConfig) {
        this.appConfig = appConfig;
    }

    @Override
    public SystemInfoResult getSystemInfo() {

        // TODO 19/11/2021 AT: Change this to walk the config tree and build a hash map
        final Map<String, Object> detailMap = HealthCheckUtils.beanToMap(appConfig);

        // We don't really want passwords appearing so mask them out.
        HealthCheckUtils.maskPasswords(detailMap);

        return new SystemInfoResult("Stroom config", null, detailMap);
    }
}
