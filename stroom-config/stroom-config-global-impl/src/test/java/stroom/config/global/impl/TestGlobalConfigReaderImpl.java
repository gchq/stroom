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

import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.ListConfigResponse;
import stroom.config.global.shared.OverrideValue;
import stroom.node.api.NodeService;
import stroom.util.shared.PageResponse;
import stroom.util.shared.PropertyPath;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestGlobalConfigReaderImpl {

    private static final PropertyPath PASSWORD_PATH = PropertyPath.fromPathString("stroom.smtp.password");
    private static final PropertyPath TITLE_PATH = PropertyPath.fromPathString("stroom.ui.htmlTitle");

    private final GlobalConfigService globalConfigService = Mockito.mock(GlobalConfigService.class);
    private final NodeService nodeService = Mockito.mock(NodeService.class);
    private final GlobalConfigReaderImpl reader = new GlobalConfigReaderImpl(() -> globalConfigService,
            () -> nodeService);

    private final ConfigProperty password = ConfigProperty.builder()
            .name(PASSWORD_PATH)
            .password(true)
            .defaultValue("defpass")
            .databaseOverrideValue("dbpass")
            .build();
    private final ConfigProperty title = ConfigProperty.builder()
            .name(TITLE_PATH)
            .defaultValue("Stroom")
            .yamlOverrideValue("My Stroom")
            .build();

    @Test
    void testListRemovesTheValuesOfPasswords() {
        Mockito.when(globalConfigService.list()).thenReturn(new ListConfigResponse(List.of(password, title),
                PageResponse.empty(), "node1"));

        final List<ConfigProperty> listed = reader.list();

        assertThat(listed).hasSize(2);
        final ConfigProperty sanitised = listed.getFirst();
        assertThat(sanitised.getDefaultValue()).isEmpty();
        assertThat(sanitised.getDatabaseOverrideValue().isHasOverride()).isTrue();
        assertThat(sanitised.getDatabaseOverrideValue().getValue()).isNull();
        assertThat(sanitised.getYamlOverrideValue().isHasOverride()).isFalse();
        assertThat(listed.get(1)).isEqualTo(title);
        assertThat(listed.get(1).getYamlOverrideValue().getValue()).isEqualTo("My Stroom");
    }

    @Test
    void testFetchRemovesTheValuesOfPasswords() {
        Mockito.when(globalConfigService.fetch(PASSWORD_PATH)).thenReturn(Optional.of(password));

        final ConfigProperty fetched = reader.fetch(PASSWORD_PATH).orElseThrow();

        assertThat(fetched.getEffectiveValue()).isEmpty();
        assertThat(fetched.getDatabaseOverrideValue().isHasOverride()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTheYamlValueOfThisNodeIsFoundLocally() {
        Mockito.when(globalConfigService.fetch(TITLE_PATH)).thenReturn(Optional.of(title));
        Mockito.when(nodeService.remoteRestResult(Mockito.eq("node1"), Mockito.any(Supplier.class),
                        Mockito.any(Supplier.class), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(2).get());

        final OverrideValue<String> yaml = reader.fetchYamlOverrideValue(TITLE_PATH, "node1");

        assertThat(yaml.isHasOverride()).isTrue();
        assertThat(yaml.getValue()).isEqualTo("My Stroom");
    }

    @Test
    @SuppressWarnings("unchecked")
    void testTheYamlValueOfAnUnknownPropertyIsAnError() {
        Mockito.when(globalConfigService.fetch(Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(nodeService.remoteRestResult(Mockito.eq("node1"), Mockito.any(Supplier.class),
                        Mockito.any(Supplier.class), Mockito.any(), Mockito.any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(2).get());

        assertThatThrownBy(() -> reader.fetchYamlOverrideValue(TITLE_PATH, "node1"))
                .hasMessageContaining("Property stroom.ui.htmlTitle not found");
    }
}
