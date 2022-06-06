package stroom.app.resources;

import stroom.core.sysinfo.SystemInfoResource;
import stroom.core.sysinfo.SystemInfoResourceImpl;
import stroom.core.sysinfo.SystemInfoService;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.mock.MockStroomEventLoggingService;
import stroom.test.common.util.test.AbstractResourceTest;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.SystemInfoResult;
import stroom.util.sysinfo.SystemInfoResultList;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import javax.validation.constraints.NotNull;

import static org.mockito.Mockito.when;


class TestSystemInfoResourceImpl extends AbstractResourceTest<SystemInfoResource> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestSystemInfoResourceImpl.class);

    private final StroomEventLoggingService stroomEventLoggingService = new MockStroomEventLoggingService();

    @Mock
    private SystemInfoService systemInfoService;

    @Test
    void getAll() {

        final HasSystemInfo systemInfoSupplier1 = getSystemInfoSupplier("name1");
        final HasSystemInfo systemInfoSupplier2 = getSystemInfoSupplier("name2");

        final SystemInfoResultList expectedResults = SystemInfoResultList.of(
                systemInfoSupplier1.getSystemInfo(),
                systemInfoSupplier2.getSystemInfo());

        when(systemInfoService.getAll())
                .thenAnswer(invocation -> {
                    LOGGER.info("SystemInfoService.getAll() mock called");

                    return List.of(
                            systemInfoSupplier1.getSystemInfo(),
                            systemInfoSupplier2.getSystemInfo());
                });

        final SystemInfoResultList result = doGetTest("/", SystemInfoResultList.class, expectedResults);
    }

    @NotNull
    private HasSystemInfo getSystemInfoSupplier(final String name) {

        return new HasSystemInfo() {
            @Override
            public String getSystemInfoName() {
                return name;
            }

            @Override
            public SystemInfoResult getSystemInfo() {
                return SystemInfoResult.builder(this)
                        .addDetail("key1", "value1")
                        .addDetail("key2", "value2")
                        .build();
            }
        };
    }

    @Override
    public SystemInfoResource getRestResource() {
        return new SystemInfoResourceImpl(() -> systemInfoService, () -> stroomEventLoggingService);
    }

    @Override
    public String getResourceBasePath() {
        return SystemInfoResource.BASE_PATH;
    }
}
