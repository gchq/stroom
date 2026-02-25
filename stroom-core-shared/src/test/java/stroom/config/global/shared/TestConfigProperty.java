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

package stroom.config.global.shared;

import stroom.util.json.JsonUtil;
import stroom.util.shared.PropertyPath;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TestConfigProperty {

    // No LOGGER, due to GWT

    @Test
    void testPrecedenceNullDefaultOnly() {
        final ConfigProperty configProperty = ConfigProperty.builder()
                .build();

        assertThat(configProperty.getDefaultValue())
                .isEmpty();

        assertThat(configProperty.getDatabaseOverrideValue().isHasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getDatabaseOverrideValue().getValueAsOptional()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getYamlOverrideValue().isHasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getYamlOverrideValue().getValueAsOptional()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getEffectiveValue())
                .isEmpty();
    }

    @Test
    void testPrecedenceDefaultOnly() {
        final String defaultValue = "default";
        final ConfigProperty configProperty = ConfigProperty.builder()
                .defaultValue(defaultValue)
                .build();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);
        assertThat(configProperty.getDatabaseOverrideValue().isHasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getDatabaseOverrideValue().getValueAsOptional()))
                .isInstanceOf(RuntimeException.class);
        assertThat(configProperty.getYamlOverrideValue().isHasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getYamlOverrideValue().getValueAsOptional()))
                .isInstanceOf(RuntimeException.class);
        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(defaultValue);
    }

    @Test
    void testPrecedenceDefaultAndDb() {
        final String defaultValue = "default";
        final String dbValue = "database";
        final ConfigProperty configProperty = ConfigProperty.builder()
                .defaultValue(defaultValue)
                .databaseOverrideValue(dbValue)
                .build();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().isHasOverride())
                .isTrue();
        assertThat(configProperty.getDatabaseOverrideValue().getValueAsOptional().orElseThrow())
                .isEqualTo(dbValue);

        assertThat(configProperty.getYamlOverrideValue().isHasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getYamlOverrideValue().getValueAsOptional()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(dbValue);
    }

    @Test
    void testPrecedenceDefaultAndNullDb() {
        final String defaultValue = "default";
        final String dbValue = null;
        final ConfigProperty configProperty = ConfigProperty.builder()
                .defaultValue(defaultValue)
                .databaseOverrideValue(dbValue)
                .build();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);
        assertThat(configProperty.getDatabaseOverrideValue().isHasOverride())
                .isTrue();
        assertThat(configProperty.getDatabaseOverrideValue().getValueAsOptional())
                .isEmpty();
        assertThat(configProperty.getYamlOverrideValue().isHasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getYamlOverrideValue().getValueAsOptional()))
                .isInstanceOf(RuntimeException.class);
        assertThat(configProperty.getEffectiveValue())
                .isEmpty();
    }

    @Test
    void testPrecedenceDefaultAndYaml() {
        final String defaultValue = "default";
        final String yamlValue = "yaml";
        final ConfigProperty configProperty = ConfigProperty.builder()
                .defaultValue(defaultValue)
                .yamlOverrideValue(yamlValue)
                .build();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().isHasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getDatabaseOverrideValue().getValueAsOptional()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getYamlOverrideValue().isHasOverride())
                .isTrue();
        assertThat(configProperty.getYamlOverrideValue().getValueAsOptional().orElseThrow())
                .isEqualTo(yamlValue);

        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(yamlValue);
    }

    @Test
    void testPrecedenceDefaultAndNullYaml() {
        final String defaultValue = "default";
        final String yamlValue = null;
        final ConfigProperty configProperty = ConfigProperty.builder()
                .defaultValue(defaultValue)
                .yamlOverrideValue(yamlValue)
                .build();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().isHasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getDatabaseOverrideValue().getValueAsOptional()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getYamlOverrideValue().isHasOverride())
                .isTrue();
        assertThat(configProperty.getYamlOverrideValue().getValueAsOptional())
                .isEmpty();

        assertThat(configProperty.getEffectiveValue())
                .isEmpty();
    }

    @Test
    void testPrecedenceDefaultAndDbAndYaml() {
        final String defaultValue = "default";
        final String dbValue = "database";
        final String yamlValue = "yaml";
        final ConfigProperty configProperty = ConfigProperty.builder()
                .defaultValue(defaultValue)
                .databaseOverrideValue(dbValue)
                .yamlOverrideValue(yamlValue)
                .build();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().isHasOverride())
                .isTrue();
        assertThat(configProperty.getDatabaseOverrideValue().getValueAsOptional().orElseThrow())
                .isEqualTo(dbValue);

        assertThat(configProperty.getYamlOverrideValue().isHasOverride())
                .isTrue();
        assertThat(configProperty.getYamlOverrideValue().getValueAsOptional().orElseThrow())
                .isEqualTo(yamlValue);

        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(yamlValue);
    }

    @Test
    void testPrecedenceDefaultAndDbAndNullYaml() {
        final String defaultValue = "default";
        final String dbValue = "database";
        final String yamlValue = null;
        ConfigProperty configProperty = ConfigProperty.builder()
                .defaultValue(defaultValue)
                .databaseOverrideValue(dbValue)
                .yamlOverrideValue(yamlValue)
                .build();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().isHasOverride())
                .isTrue();
        assertThat(configProperty.getDatabaseOverrideValue().getValueAsOptional().orElseThrow())
                .isEqualTo(dbValue);

        assertThat(configProperty.getYamlOverrideValue().isHasOverride())
                .isTrue();
        assertThat(configProperty.getYamlOverrideValue().getValueAsOptional())
                .isEmpty();

        assertThat(configProperty.getEffectiveValue())
                .isEmpty();

        // Now remove the yaml override
        configProperty = configProperty.copy().removeYamlOverride().build();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().isHasOverride())
                .isTrue();
        assertThat(configProperty.getDatabaseOverrideValue().getValueAsOptional().orElseThrow())
                .isEqualTo(dbValue);

        assertThat(configProperty.getYamlOverrideValue().isHasOverride())
                .isFalse();
        final ConfigProperty cp1 = configProperty;
        assertThat(Assertions.catchThrowable(() -> cp1.getYamlOverrideValue().getValueAsOptional()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(dbValue);

        // now remove the db override
        configProperty = configProperty.copy().removeDatabaseOverride().build();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().isHasOverride())
                .isFalse();
        final ConfigProperty cp2 = configProperty;
        assertThat(Assertions.catchThrowable(() -> cp2.getDatabaseOverrideValue().getValueAsOptional()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getYamlOverrideValue().isHasOverride())
                .isFalse();
        final ConfigProperty cp3 = configProperty;
        assertThat(Assertions.catchThrowable(() -> cp3.getYamlOverrideValue().getValueAsOptional()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(defaultValue);
    }

    @Test
    void testPassword() {
        final String defaultValue = "default";
        final ConfigProperty configProperty = ConfigProperty.builder()
                .defaultValue(defaultValue)
                .password(true)
                .build();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getEffectiveValueMasked().orElseThrow())
                .startsWith("*****");

        assertThat(configProperty.isPassword())
                .isTrue();
    }

    @Test
    void testSerialisation1() throws IOException {
        final ConfigProperty configProperty = ConfigProperty.builder()
                .id(123)
                .name(PropertyPath.fromPathString("stroom.node.name"))
                .editable(true)
                .password(false)
                .requireRestart(true)
                .requireUiRestart(true)
                .defaultValue("default-123")
                .yamlOverrideValue("yaml-123")
                .databaseOverrideValue("db-123")
                .build();
        doSerdeTest(configProperty);
    }

    @Test
    void testSerialisation2() throws IOException {
        final ConfigProperty configProperty = ConfigProperty
                .builder()
                .name(PropertyPath.fromPathString("some.name"))
                .build();
        doSerdeTest(configProperty);
    }

    private void doSerdeTest(final ConfigProperty configProperty) throws IOException {
        final ObjectMapper mapper = JsonUtil.getMapper();
        assertThat(mapper.canSerialize(ConfigProperty.class)).isTrue();
        assertThat(mapper.canSerialize(OverrideValue.class)).isTrue();

        final String json = mapper.writeValueAsString(configProperty);
        System.out.println("\n" + json);

        final ConfigProperty configProperty2 = mapper.readValue(json, ConfigProperty.class);

        assertThat(configProperty2).isEqualTo(configProperty);
    }
}
