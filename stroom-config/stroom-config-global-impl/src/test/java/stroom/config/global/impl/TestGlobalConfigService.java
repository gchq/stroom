/*
 * Copyright 2026 Crown Copyright
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

package stroom.config.global.impl;

import stroom.config.global.impl.TestConfigMapper.TestConfig;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ConfigPropertyValidationException;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.PropertyPath;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class TestGlobalConfigService {

    private static final PropertyPath SECURE_MODE = PropertyPath.fromPathString("stroom.pipeline.xslt.secureMode");

    private ConfigPropertyDao dao;
    private ConfigMapper configMapper;
    private GlobalConfigService globalConfigService;

    @BeforeEach
    void setUp() {
        dao = Mockito.mock(ConfigPropertyDao.class);
        configMapper = new ConfigMapper(new TestConfig(), TestConfig::new);
        final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
        Mockito.when(securityContext.secureResult(any(AppPermission.class), any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        globalConfigService = new GlobalConfigService(
                Mockito.mock(GlobalConfigBootstrapService.class),
                dao,
                securityContext,
                configMapper,
                null,
                null,
                null,
                null);
    }

    @Test
    void testUpdateReadOnlyPropertyRefused() {
        final ConfigProperty configProperty = configMapper.getGlobalProperty(SECURE_MODE).orElseThrow()
                .copy()
                .databaseOverrideValue("false")
                .build();

        assertThatThrownBy(() -> globalConfigService.update(configProperty))
                .isInstanceOf(ConfigPropertyValidationException.class)
                .hasMessageContaining("read-only");
        Mockito.verifyNoInteractions(dao);
        assertThat(configMapper.getConfigObject(TestConfig.class).getPipelineConfig().getXsltConfig().isSecureMode())
                .isTrue();
    }

    @Test
    void testClearingReadOnlyPropertyAllowed() {
        // Removing a database value left over from before a property was read-only is still allowed.
        final ConfigProperty configProperty = configMapper.getGlobalProperty(SECURE_MODE).orElseThrow()
                .copy()
                .id(1)
                .build();

        final ConfigProperty updated = globalConfigService.update(configProperty);

        Mockito.verify(dao).delete(SECURE_MODE);
        assertThat(updated.hasDatabaseOverride()).isFalse();
    }
}
