/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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
