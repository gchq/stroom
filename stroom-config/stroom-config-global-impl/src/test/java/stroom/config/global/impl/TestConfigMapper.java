package stroom.config.global.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;
import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationFactoryFactory;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.FileConfigurationSourceProvider;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jackson.Jackson;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.OverrideValue;
import stroom.docref.DocRef;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestConfigMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestConfigMapper.class);

    @Test
    void getGlobalProperties() throws IOException, ConfigurationException {

        AppConfig appConfig = getAppConfig();
        ConfigMapper configMapper = new ConfigMapper(appConfig);

        Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        String txt = configProperties.stream()
                .sorted(Comparator.comparing(ConfigProperty::getName))
                .map(configProperty ->
                        LogUtil.message("{} - [{}] - [{}] - [{}] - [{}] - [{}] - [{}]",
                                configProperty.getName(),
                                configProperty.getDefaultValue().orElse(""),
                                configProperty.getDatabaseOverrideValue().getValueOrElse("UNSET", null),
                                configProperty.getYamlOverrideValue().getValueOrElse("UNSET", null),
                                configProperty.getEffectiveValue().orElse(""),
                                configProperty.getSource(),
                                configProperty.getDescription()))
                .collect(Collectors.joining("\n"));

        LOGGER.debug("Properties\n{}", txt);
    }

    @Test
    void getDataTypeNames() {
        TestConfig appConfig = new TestConfig();
        ConfigMapper configMapper = new ConfigMapper(appConfig);

        Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        String txt = configProperties.stream()
                .sorted(Comparator.comparing(ConfigProperty::getName))
                .map(configProperty ->
                        LogUtil.message("{}, {}", configProperty.getName(), configProperty.getDataTypeName()))
                .collect(Collectors.joining("\n"));

        LOGGER.debug("Properties\n{}", txt);
    }

    @Test
    void testSerdeAllProperties() {
        AppConfig appConfig = getAppConfig();
        ConfigMapper configMapper = new ConfigMapper(appConfig);

        Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        // getting each prop as a ConfigProperty ensure we can serialise to string
        configProperties.forEach(configProperty -> {
            configProperty.setDatabaseOverrideValue(configProperty.getDefaultValue().orElse(null));

            // verify we can convert back to an object from a string
            ConfigProperty newConfigProperty = configMapper.decorateDbConfigProperty(configProperty);

            LOGGER.debug(configProperty.toString());
            assertThat(newConfigProperty.getSource())
                    .isIn(ConfigProperty.SourceType.DATABASE, ConfigProperty.SourceType.DEFAULT);
        });
    }


    @Test
    void testValidatePropertyPath_valid() {
        AppConfig appConfig = new AppConfig();
        ConfigMapper configMapper = new ConfigMapper(appConfig);

        boolean isValid = configMapper.validatePropertyPath("stroom.ui.aboutHtml");

        assertThat(isValid).isTrue();
    }

    @Test
    void testValidatePropertyPath_invalid() {
        AppConfig appConfig = new AppConfig();
        ConfigMapper configMapper = new ConfigMapper(appConfig);

        boolean isValid = configMapper.validatePropertyPath("stroom.unknown.prop");

        assertThat(isValid).isFalse();
    }


    private void assertValues(final ConfigProperty configProperty,
                              final String expectedDefault,
                              final OverrideValue<String> expectedDatabase,
                              final OverrideValue<String> expectedYaml,
                              final String expectedEffective) {

        assertThat(configProperty.getDatabaseOverrideValue())
                .isEqualTo(expectedDatabase);
        assertThat(configProperty.getDefaultValue())
                .isEqualTo(Optional.ofNullable(expectedDefault));
        assertThat(configProperty.getYamlOverrideValue())
                .isEqualTo(expectedYaml);
        assertThat(configProperty.getEffectiveValue())
                .isEqualTo(Optional.ofNullable(expectedEffective));
    }

    @Test
    void testGetGlobalProperties_defaultValueWithValue() throws IOException, ConfigurationException {

        AppConfig appConfig = getAppConfig();

        // simulate dropwiz setting a prop from the yaml
        String initialValue = appConfig.getPipelineConfig().getReferenceDataConfig().getLocalDir();
        String newValue = initialValue + "xxx";
        appConfig.getPipelineConfig().getReferenceDataConfig().setLocalDir(newValue);

        ConfigMapper configMapper = new ConfigMapper(appConfig);

        final Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        final ConfigProperty configProperty = configProperties.stream()
                .filter(confProp -> confProp.getName().equalsIgnoreCase("stroom.pipeline.referenceData.localDir"))
                .findFirst()
                .orElseThrow();

        assertValues(configProperty,
                initialValue,
                OverrideValue.unSet(),
                OverrideValue.with(newValue),
                newValue);
    }

    @Test
    void testGetGlobalProperties_defaultValueWithNullValue() throws IOException, ConfigurationException {

        AppConfig appConfig = getAppConfig();

        // simulate a prop not being defined in the yaml
        String initialValue = appConfig.getPipelineConfig().getReferenceDataConfig().getLocalDir();
        String newYamlValue = null;
        appConfig.getPipelineConfig().getReferenceDataConfig().setLocalDir(newYamlValue);

        ConfigMapper configMapper = new ConfigMapper(appConfig);

        final Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        final ConfigProperty configProperty = configProperties.stream()
                .filter(confProp -> confProp.getName().equalsIgnoreCase("stroom.pipeline.referenceData.localDir"))
                .findFirst()
                .orElseThrow();

        assertValues(
                configProperty,
                initialValue,
                OverrideValue.unSet(),
                OverrideValue.with(newYamlValue),
                newYamlValue);

//        assertThat(configProperty.getValue()).isEqualTo(initialValue);
//        assertThat(configProperty.getDefaultValue()).isEqualTo(initialValue);
    }

    @Test
    void update_string() throws IOException, ConfigurationException {
        AppConfig appConfig = getAppConfig();

        Supplier<String> getter = () -> appConfig.getPathConfig().getTemp();
        String initialValue = getter.get();
        String newValue = initialValue + "/xxx";
        String fullPath = "stroom.path.temp";

        ConfigMapper configMapper = new ConfigMapper(appConfig);
        ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        configProperty.setDatabaseOverrideValue(newValue);
        configMapper.decorateDbConfigProperty(configProperty);

        assertThat(getter.get()).isEqualTo(newValue);
    }
    @Test
    void update_boolean() throws IOException, ConfigurationException {
        AppConfig appConfig = getAppConfig();

        BooleanSupplier getter = () -> appConfig.getPipelineConfig().getReferenceDataConfig().isReadAheadEnabled();
        boolean initialValue = getter.getAsBoolean();
        boolean newValue = !initialValue;
        String fullPath = "stroom.pipeline.referenceData.readAheadEnabled";

        ConfigMapper configMapper = new ConfigMapper(appConfig);
        ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        configProperty.setDatabaseOverrideValue(Boolean.valueOf(newValue).toString().toLowerCase());
        configMapper.decorateDbConfigProperty(configProperty);

        assertThat(getter.getAsBoolean()).isEqualTo(newValue);
    }

    @Test
    void update_int() throws IOException, ConfigurationException {
        AppConfig appConfig = getAppConfig();

        IntSupplier getter = () -> appConfig.getPipelineConfig().getReferenceDataConfig().getMaxPutsBeforeCommit();
        int initialValue = getter.getAsInt();
        int newValue = initialValue + 1;
        String fullPath = "stroom.pipeline.referenceData.maxPutsBeforeCommit";

        ConfigMapper configMapper = new ConfigMapper(appConfig);
        ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        configProperty.setDatabaseOverrideValue(Integer.valueOf(newValue).toString());
        configMapper.decorateDbConfigProperty(configProperty);

        assertThat(getter.getAsInt()).isEqualTo(newValue);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    @Test
    void testGetGlobalProperties2() {
        TestConfig testConfig = new TestConfig();

        ConfigMapper configMapper = new ConfigMapper(testConfig);

        Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        configProperties.forEach(configProperty ->
                LOGGER.debug("{} - {}", configProperty.getName(), configProperty.getEffectiveValue().orElse(null)));
    }


    @Test
    void update_string2() {
        TestConfig testConfig = new TestConfig();

        Supplier<String> getter = testConfig::getStringProp;
        String initialValue = getter.get();
        String newValue = initialValue + "xxx";
        String fullPath = "stroom.stringProp";

        ConfigMapper configMapper = new ConfigMapper(testConfig);
        ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        configProperty.setDatabaseOverrideValue(newValue);
        configMapper.decorateDbConfigProperty(configProperty);

        assertThat(getter.get()).isEqualTo(newValue);
    }

    @Test
    void update_primitiveInt() {
        TestConfig testConfig = new TestConfig();

        IntSupplier getter = () -> testConfig.getTestPrimitiveConfig().getIntProp();
        int initialValue = getter.getAsInt();
        int newValue = initialValue + 1;
        String fullPath = "stroom.primitive.intProp";

        ConfigMapper configMapper = new ConfigMapper(testConfig);
        ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        configProperty.setDatabaseOverrideValue(Integer.valueOf(newValue).toString());
        configMapper.decorateDbConfigProperty(configProperty);

        assertThat(getter.getAsInt()).isEqualTo(newValue);
    }

    @Test
    void update_boxedInt() {
        TestConfig testConfig = new TestConfig();

        Supplier<Integer> getter = () -> testConfig.getTestBoxedConfig().getIntProp();
        Integer initialValue = getter.get();
        Integer newValue = initialValue + 1;
        String fullPath = "stroom.boxed.intProp";

        ConfigMapper configMapper = new ConfigMapper(testConfig);
        ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        configProperty.setDatabaseOverrideValue(newValue.toString());
        configMapper.decorateDbConfigProperty(configProperty);

        assertThat(getter.get()).isEqualTo(newValue);
    }

    @Test
    void update_docRef() {
        TestConfig testConfig = new TestConfig();
        ConfigMapper configMapper = new ConfigMapper(testConfig);

        LOGGER.debug("Initial UUID {}", testConfig.getDocRefProp().getUuid());

        Supplier<DocRef> getter = testConfig::getDocRefProp;
        DocRef initialValue = getter.get();
        DocRef newValue = new DocRef.Builder()
                .type(initialValue.getType() + "xxx")
                .uuid(UUID.randomUUID().toString())
                .name(initialValue.getName() + "zzz")
                .build();
        String fullPath = "stroom.docRefProp";

        LOGGER.debug("New UUID {}", newValue.getUuid());

        ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        configProperty.setDatabaseOverrideValue(ConfigMapper.convertToString(newValue));
        configMapper.decorateDbConfigProperty(configProperty);

        assertThat(getter.get()).isEqualTo(newValue);
    }

    @Test
    void update_enum() {
        TestConfig testConfig = new TestConfig();
        ConfigMapper configMapper = new ConfigMapper(testConfig);

        Supplier<TestConfig.State> getter = testConfig::getStateProp;
        TestConfig.State initialValue = getter.get();
        TestConfig.State newValue = TestConfig.State.ON;
        String fullPath = "stroom.stateProp";

        ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        configProperty.setDatabaseOverrideValue(ConfigMapper.convertToString(newValue));
        configMapper.decorateDbConfigProperty(configProperty);

        assertThat(initialValue).isEqualTo(TestConfig.State.OFF);
        assertThat(getter.get()).isEqualTo(newValue);
    }

    @Test
    void update_docRefList() {
        TestConfig testConfig = new TestConfig();
        ConfigMapper configMapper = new ConfigMapper(testConfig);

        Supplier<List<DocRef>> getter = testConfig::getDocRefListProp;
        List<DocRef> initialValue = getter.get();
        List<DocRef> newValue = new ArrayList<>();
        initialValue.forEach(docRef ->
                newValue.add(new DocRef.Builder()
                        .type(docRef.getType() + "x")
                        .uuid(UUID.randomUUID().toString())
                        .name(docRef.getName() + "xx")
                        .build()));
        newValue.add(new DocRef.Builder()
                .type("NewDocRefType")
                .uuid(UUID.randomUUID().toString())
                .build());
        String fullPath = "stroom.docRefListProp";

        ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        configProperty.setDatabaseOverrideValue(ConfigMapper.convertToString(newValue));
        configMapper.decorateDbConfigProperty(configProperty);

        assertThat(getter.get()).isEqualTo(newValue);
    }

    @Test
    void update_enumList() {
        TestConfig testConfig = new TestConfig();
        ConfigMapper configMapper = new ConfigMapper(testConfig);

        Supplier<List<TestConfig.State>> getter = testConfig::getStateListProp;
        List<TestConfig.State> initialValue = getter.get();
        List<TestConfig.State> newValue = new ArrayList<>(initialValue);
        newValue.add(TestConfig.State.ON);
        String fullPath = "stroom.stateListProp";

        ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        configProperty.setDatabaseOverrideValue(ConfigMapper.convertToString(newValue));
        configMapper.decorateDbConfigProperty(configProperty);

        assertThat(getter.get()).isEqualTo(newValue);
    }

    @Test
    void update_stringList() {
        TestConfig testConfig = new TestConfig();
        ConfigMapper configMapper = new ConfigMapper(testConfig);

        Supplier<List<String>> getter = () -> testConfig.getStringListProp();
        List<String> initialValue = getter.get();
        List<String> newValue = Stream.of(initialValue, initialValue)
                .flatMap(List::stream)
                .map(str -> str + "x")
                .collect(Collectors.toList());
        String fullPath = "stroom.stringListProp";

        ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        configProperty.setDatabaseOverrideValue(ConfigMapper.convertToString(newValue));
        configMapper.decorateDbConfigProperty(configProperty);

        assertThat(getter.get()).isEqualTo(newValue);
    }

    @Test
    void update_stringLongMap() {
        TestConfig testConfig = new TestConfig();
        ConfigMapper configMapper = new ConfigMapper(testConfig);

        Supplier<Map<String, Long>> getter = testConfig::getStringLongMapProp;
        Map<String, Long> initialValue = getter.get();
        Map<String, Long> newValue = new HashMap<>();
        initialValue.forEach((k, v) ->
                newValue.put(k, v + 10));
        newValue.put("k4", 14L);
        String fullPath = "stroom.stringLongMapProp";

        String newValueStr = ConfigMapper.convertToString(newValue);

        ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        configProperty.setDatabaseOverrideValue(ConfigMapper.convertToString(newValue));
        configMapper.decorateDbConfigProperty(configProperty);

        assertThat(getter.get()).isEqualTo(newValue);
    }

    @Test
    void testPrecedenceDefaultOnly() {
        TestConfig testConfig = new TestConfig();
        String defaultValue = testConfig.getStringProp();

        ConfigMapper configMapper = new ConfigMapper(testConfig);

        ConfigProperty configProperty = configMapper.getGlobalProperty("stroom.stringProp").orElseThrow();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);
        assertThat(configProperty.getDatabaseOverrideValue().hasOverride())
                .isFalse();
        assertThat(configProperty.getYamlOverrideValue().hasOverride())
                .isFalse();
        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(defaultValue);
    }


    @Test
    void testValidateDelimiter_bad() {
        Assertions.assertThrows(RuntimeException.class, () -> {
            ConfigMapper.validateDelimiter("xxxx", 0, "first", "dummy example");
        });
    }

    @Test
    void testValidateDelimiter_good() {
        // Will throw an exception if bad
        ConfigMapper.validateDelimiter("|a|b|c", 0, "first", "dummy example");
    }

    @Test
    void testValidateStringValue_list_good1() {
        doValidateStringValueTest("stroom.stringListProp", "|item1|item2|item3", true);
    }

    @Test
    void testValidateStringValue_list_good2() {
        doValidateStringValueTest("stroom.stringListProp", "", true);
    }

    @Test
    void testValidateStringValue_list_good3() {
        doValidateStringValueTest("stroom.stringListProp", "#item1", true);
    }

    @Test
    void testValidateStringValue_list_bad() {
        doValidateStringValueTest("stroom.stringListProp", "item1|item2|item3", false);
    }

    @Test
    void testValidateStringValue_map_good() {
        doValidateStringValueTest("stroom.stringLongMapProp", "@#key1#123@key2#456", true);
    }

    @Test
    void testValidateStringValue_map_bad() {
        // $ not valid delimiter
        doValidateStringValueTest("stroom.stringLongMapProp", "@$key1$123@key2$456", false);
    }

    @Test
    void testValidateStringValue_docRefList_good() {
        doValidateStringValueTest("stroom.docRefListProp", ",|docRef(type1|uuid1|name1),|docRef(type1|uuid1|name1)", true);
    }

    @Test
    void testValidateStringValue_docRefList_bad() {
        // $ not valid delimiter
        doValidateStringValueTest("stroom.docRefListProp", ",$docRef(type1$uuid1$name1),$docRef(type1$uuid1$name1)", false);
    }

    @Test
    void testValidateStringValue_docRef_good() {
        doValidateStringValueTest("stroom.docRefProp", ",docRef(type1,uuid1,name1)", true);
    }

    @Test
    void testValidateStringValue_docRef_bad1() {
        // $ not valid delimiter
        doValidateStringValueTest("stroom.docRefProp", "docRef(type1,uuid1,name1)", false);
    }

    @Test
    void testValidateStringValue_docRef_bad2() {
        // $ not valid delimiter
        doValidateStringValueTest("stroom.docRefProp", ",docRef(type1,uuid1)", false);
    }

    @Test
    void testValidateStringValue_path_good() {
        // $ not valid delimiter
        doValidateStringValueTest("stroom.pathProp", "/h/j/k/l", true);
    }

    @Test
    void testValidateStringValue_duration_good() {
        doValidateStringValueTest("stroom.durationProp", "P1DT1M", true);
    }

    @Test
    void testValidateStringValue_duration_bad() {
        doValidateStringValueTest("stroom.durationProp", "1D", false);
    }


    private void doValidateStringValueTest(final String path, final String value, boolean shouldValidate) {
        TestConfig testConfig = new TestConfig();
        ConfigMapper configMapper = new ConfigMapper(testConfig);

        if (shouldValidate) {
            configMapper.validateStringValue(path, value);
        } else {
            Assertions.assertThrows(RuntimeException.class, () -> {
                // no leading delimiter
                configMapper.validateStringValue(path, value);
            });
        }
    }

    private AppConfig getAppConfig() {
        return new AppConfig();
    }

    private AppConfig getDevYamlAppConfig() throws IOException, ConfigurationException {
        ConfigurationSourceProvider configurationSourceProvider = new SubstitutingSourceProvider(
                new FileConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false));

        ConfigurationFactoryFactory<Config> configurationFactoryFactory = new DefaultConfigurationFactoryFactory<>();

        final ConfigurationFactory<Config> configurationFactory = configurationFactoryFactory
                .create(
                        Config.class,
                        io.dropwizard.jersey.validation.Validators.newValidator(),
                        Jackson.newObjectMapper(),
                        "dw");
        Config config = configurationFactory.build(configurationSourceProvider, "../../stroom-app/dev.yml");

        return config.getAppConfig();
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    private static class Config extends Configuration {
        private AppConfig appConfig;

        AppConfig getAppConfig() {
            return appConfig;
        }

        void setAppConfig(final AppConfig appConfig) {
            this.appConfig = appConfig;
        }
    }

//    private static class ExtendedAppConfig extends AppConfig {
//
//        private TestConfig testConfig = new TestConfig();
//
//        @JsonProperty("test")
//        TestConfig getTestConfig() {
//            return testConfig;
//        }
//
//        void setTestConfig(final TestConfig testConfig) {
//            this.testConfig = testConfig;
//        }
//    }

    public static class TestConfig extends AppConfig {
        private String stringProp = "initial value";
        private List<String> stringListProp = new ArrayList<>();
        private List<Integer> intListProp = new ArrayList<>();
        private Map<String, Long> stringLongMapProp = new HashMap<>();
        private DocRef docRefProp = new DocRef("MyType", "9d9ff899-c6db-46c1-97bf-a8015a853b38", "MyName");
        private List<DocRef> docRefListProp = List.of(
                new DocRef("MyType1", "9457f9ff-eb2a-4ef1-b60d-769e9a987cd2", "MyDocRef1"),
                new DocRef("MyType2", "56068221-1a7d-486c-9fa7-af8b98733e53", "MyDocRef2"));
        private State stateProp = State.OFF;
        private List<State> stateListProp = List.of(State.ON, State.IN_BETWEEN);
        private Path pathProp = Path.of("/a/b/c/d");
        private Duration durationProp = Duration.ofMinutes(5);

        // sub-configs
        private TestPrimitiveConfig testPrimitiveConfig = new TestPrimitiveConfig();
        private TestBoxedConfig testBoxedConfig = new TestBoxedConfig();

        public TestConfig() {
            stringListProp.add("item 1");
            stringListProp.add("item 2");
            stringListProp.add("item 3");

            intListProp.add(1);
            intListProp.add(2);
            intListProp.add(3);

            stringLongMapProp.put("k1", 1L);
            stringLongMapProp.put("k2", 2L);
            stringLongMapProp.put("k3", 3L);
        }

        public String getStringProp() {
            return stringProp;
        }

        public void setStringProp(final String stringValue) {
            this.stringProp = stringValue;
        }

        @JsonProperty("primitive")
        public TestPrimitiveConfig getTestPrimitiveConfig() {
            return testPrimitiveConfig;
        }

        public void setTestPrimitiveConfig(final TestPrimitiveConfig testPrimitiveConfig) {
            this.testPrimitiveConfig = testPrimitiveConfig;
        }

        @JsonProperty("boxed")
        public TestBoxedConfig getTestBoxedConfig() {
            return testBoxedConfig;
        }

        public void setTestBoxedConfig(final TestBoxedConfig testBoxedConfig) {
            this.testBoxedConfig = testBoxedConfig;
        }

        public List<String> getStringListProp() {
            return stringListProp;
        }

        public void setStringListProp(final List<String> stringListProp) {
            this.stringListProp = stringListProp;
        }

        public List<Integer> getIntListProp() {
            return intListProp;
        }

        public void setIntListProp(final List<Integer> intListProp) {
            this.intListProp = intListProp;
        }

        public Map<String, Long> getStringLongMapProp() {
            return stringLongMapProp;
        }

        public void setStringLongMapProp(final Map<String, Long> stringLongMapProp) {
            this.stringLongMapProp = stringLongMapProp;
        }

        public DocRef getDocRefProp() {
            return docRefProp;
        }

        public void setDocRefProp(final DocRef docRefProp) {
            this.docRefProp = docRefProp;
        }

        public List<DocRef> getDocRefListProp() {
            return docRefListProp;
        }

        public void setDocRefListProp(final List<DocRef> docRefListProp) {
            this.docRefListProp = docRefListProp;
        }

        public State getStateProp() {
            return stateProp;
        }

        public void setStateProp(final State stateProp) {
            this.stateProp = stateProp;
        }

        public List<State> getStateListProp() {
            return stateListProp;
        }

        public void setStateListProp(final List<State> stateListProp) {
            this.stateListProp = stateListProp;
        }



        public Path getPathProp() {
            return pathProp;
        }

        public Duration getDurationProp() {
            return durationProp;
        }

        public void setPathProp(final Path pathProp) {
            this.pathProp = pathProp;
        }

        public void setDurationProp(final Duration durationProp) {
            this.durationProp = durationProp;
        }

        public enum State {
            ON, IN_BETWEEN, OFF
        }
    }


    public static class TestPrimitiveConfig implements IsConfig {
        private boolean booleanProp = false;
        private int intProp = 123;
        private long longProp = 123L;
        private double doubleProp = 1.23;
        private short shortProp = 123;

        public boolean isBooleanProp() {
            return booleanProp;
        }

        public void setBooleanProp(final boolean booleanProp) {
            this.booleanProp = booleanProp;
        }

        public int getIntProp() {
            return intProp;
        }

        public void setIntProp(final int intProp) {
            this.intProp = intProp;
        }

        public long getLongProp() {
            return longProp;
        }

        public void setLongProp(final long longProp) {
            this.longProp = longProp;
        }

        public double getDoubleProp() {
            return doubleProp;
        }

        public void setDoubleProp(final double doubleProp) {
            this.doubleProp = doubleProp;
        }

        public short getShortProp() {
            return shortProp;
        }

        public void setShortProp(final short shortProp) {
            this.shortProp = shortProp;
        }
    }

    public static class TestBoxedConfig implements IsConfig {
        private Boolean booleanProp = false;
        private Integer intProp = 123;
        private Long longProp = 123L;
        private Double doubleProp = 1.23;
        private Short shortProp = 123;

        public Boolean getBooleanProp() {
            return booleanProp;
        }

        public void setBooleanProp(final Boolean booleanProp) {
            this.booleanProp = booleanProp;
        }

        public Integer getIntProp() {
            return intProp;
        }

        public void setIntProp(final Integer intProp) {
            this.intProp = intProp;
        }

        public Long getLongProp() {
            return longProp;
        }

        public void setLongProp(final Long longProp) {
            this.longProp = longProp;
        }

        public Double getDoubleProp() {
            return doubleProp;
        }

        public void setDoubleProp(final Double doubleProp) {
            this.doubleProp = doubleProp;
        }

        public Short getShortProp() {
            return shortProp;
        }

        public void setShortProp(final Short shortProp) {
            this.shortProp = shortProp;
        }
    }

    public static class TestOtherTypesConfig implements IsConfig {


    }
}