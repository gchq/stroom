package stroom.config.global.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class TestConfigProperty {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestConfigProperty.class);

    @Test
    void testPrecedenceNullDefaultOnly() {

        ConfigProperty configProperty = new ConfigProperty();

        assertThat(configProperty.getDefaultValue())
                .isEmpty();

        assertThat(configProperty.getDatabaseOverrideValue().hasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getDatabaseOverrideValue().getValue()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getYamlOverrideValue().hasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getYamlOverrideValue().getValue()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getEffectiveValue())
                .isEmpty();
    }

    @Test
    void testPrecedenceDefaultOnly() {

        ConfigProperty configProperty = new ConfigProperty();
        String defaultValue = "default";
        configProperty.setDefaultValue(defaultValue);

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);
        assertThat(configProperty.getDatabaseOverrideValue().hasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getDatabaseOverrideValue().getValue()))
                .isInstanceOf(RuntimeException.class);
        assertThat(configProperty.getYamlOverrideValue().hasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getYamlOverrideValue().getValue()))
                .isInstanceOf(RuntimeException.class);
        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(defaultValue);
    }

    @Test
    void testPrecedenceDefaultAndDb() {

        ConfigProperty configProperty = new ConfigProperty();
        String defaultValue = "default";
        String dbValue = "database";
        configProperty.setDefaultValue(defaultValue);
        configProperty.setDatabaseOverrideValue(dbValue);

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().hasOverride())
                .isTrue();
        assertThat(configProperty.getDatabaseOverrideValue().getValue().orElseThrow())
                .isEqualTo(dbValue);

        assertThat(configProperty.getYamlOverrideValue().hasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getYamlOverrideValue().getValue()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(dbValue);
    }

    @Test
    void testPrecedenceDefaultAndNullDb() {

        ConfigProperty configProperty = new ConfigProperty();
        String defaultValue = "default";
        String dbValue = null;
        configProperty.setDefaultValue(defaultValue);
        configProperty.setDatabaseOverrideValue(dbValue);

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);
        assertThat(configProperty.getDatabaseOverrideValue().hasOverride())
                .isTrue();
        assertThat(configProperty.getDatabaseOverrideValue().getValue())
                .isEmpty();
        assertThat(configProperty.getYamlOverrideValue().hasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getYamlOverrideValue().getValue()))
                .isInstanceOf(RuntimeException.class);
        assertThat(configProperty.getEffectiveValue())
                .isEmpty();
    }

    @Test
    void testPrecedenceDefaultAndYaml() {

        ConfigProperty configProperty = new ConfigProperty();
        String defaultValue = "default";
        String yamlValue = "yaml";
        configProperty.setDefaultValue(defaultValue);
        configProperty.setYamlOverrideValue(yamlValue);

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().hasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getDatabaseOverrideValue().getValue()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getYamlOverrideValue().hasOverride())
                .isTrue();
        assertThat(configProperty.getYamlOverrideValue().getValue().orElseThrow())
                .isEqualTo(yamlValue);

        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(yamlValue);
    }

    @Test
    void testPrecedenceDefaultAndNullYaml() {

        ConfigProperty configProperty = new ConfigProperty();
        String defaultValue = "default";
        String yamlValue = null;
        configProperty.setDefaultValue(defaultValue);
        configProperty.setYamlOverrideValue(yamlValue);

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().hasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getDatabaseOverrideValue().getValue()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getYamlOverrideValue().hasOverride())
                .isTrue();
        assertThat(configProperty.getYamlOverrideValue().getValue())
                .isEmpty();

        assertThat(configProperty.getEffectiveValue())
                .isEmpty();
    }

    @Test
    void testPrecedenceDefaultAndDbAndYaml() {

        ConfigProperty configProperty = new ConfigProperty();
        String defaultValue = "default";
        String dbValue = "database";
        String yamlValue = "yaml";
        configProperty.setDefaultValue(defaultValue);
        configProperty.setDatabaseOverrideValue(dbValue);
        configProperty.setYamlOverrideValue(yamlValue);

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().hasOverride())
                .isTrue();
        assertThat(configProperty.getDatabaseOverrideValue().getValue().orElseThrow())
                .isEqualTo(dbValue);

        assertThat(configProperty.getYamlOverrideValue().hasOverride())
                .isTrue();
        assertThat(configProperty.getYamlOverrideValue().getValue().orElseThrow())
                .isEqualTo(yamlValue);

        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(yamlValue);
    }

    @Test
    void testPrecedenceDefaultAndDbAndNullYaml() {

        ConfigProperty configProperty = new ConfigProperty();
        String defaultValue = "default";
        String dbValue = "database";
        String yamlValue = null;
        configProperty.setDefaultValue(defaultValue);
        configProperty.setDatabaseOverrideValue(dbValue);
        configProperty.setYamlOverrideValue(yamlValue);

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().hasOverride())
                .isTrue();
        assertThat(configProperty.getDatabaseOverrideValue().getValue().orElseThrow())
                .isEqualTo(dbValue);

        assertThat(configProperty.getYamlOverrideValue().hasOverride())
                .isTrue();
        assertThat(configProperty.getYamlOverrideValue().getValue())
                .isEmpty();

        assertThat(configProperty.getEffectiveValue())
                .isEmpty();

        // Now remove the yaml override
        configProperty.removeYamlOverride();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().hasOverride())
                .isTrue();
        assertThat(configProperty.getDatabaseOverrideValue().getValue().orElseThrow())
                .isEqualTo(dbValue);

        assertThat(configProperty.getYamlOverrideValue().hasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getYamlOverrideValue().getValue()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(dbValue);

        // now remove the db override
        configProperty.removeDatabaseOverride();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);

        assertThat(configProperty.getDatabaseOverrideValue().hasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getDatabaseOverrideValue().getValue()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getYamlOverrideValue().hasOverride())
                .isFalse();
        assertThat(Assertions.catchThrowable(() -> configProperty.getYamlOverrideValue().getValue()))
                .isInstanceOf(RuntimeException.class);

        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(defaultValue);
    }

    @Test
    void testPassword() {

        ConfigProperty configProperty = new ConfigProperty();
        String defaultValue = "default";
        configProperty.setDefaultValue(defaultValue);
        configProperty.setPassword(true);

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
    void testOverrideValueSerialisation() throws JsonProcessingException {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        OverrideValue<String> overrideValue = OverrideValue.with("someValue");

        String json = mapper.writeValueAsString(overrideValue);
        LOGGER.info(json);

    }

    @Test
    void testSerialisation1() throws IOException {

        ConfigProperty configProperty = new ConfigProperty();
        configProperty.setId(123);
        configProperty.setName("name");
        configProperty.setEditable(true);
        configProperty.setPassword(false);
        configProperty.setRequireRestart(true);
        configProperty.setRequireUiRestart(true);
        configProperty.setDefaultValue("default-123");
        configProperty.setYamlOverrideValue("yaml-123");
        configProperty.setDatabaseOverrideValue("db-123");
        doSerdeTest(configProperty);
    }

    @Test
    void testSerialisation2() throws IOException {

        ConfigProperty configProperty = new ConfigProperty();
        doSerdeTest(configProperty);
    }

    private void doSerdeTest(final ConfigProperty configProperty) throws IOException {

        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

        assertThat(mapper.canSerialize(ConfigProperty.class)).isTrue();
        assertThat(mapper.canSerialize(OverrideValue.class)).isTrue();

        String json = mapper.writeValueAsString(configProperty);
        LOGGER.info(json);

        final ConfigProperty configProperty2 = mapper.readValue(json, ConfigProperty.class);

        assertThat(configProperty2).isEqualTo(configProperty);
    }
}