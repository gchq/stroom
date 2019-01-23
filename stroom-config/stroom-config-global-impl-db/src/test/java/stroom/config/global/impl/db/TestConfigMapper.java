package stroom.config.global.impl.db;

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
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.global.api.ConfigProperty;
import stroom.docref.DocRef;
import stroom.util.logging.LambdaLogger;
import stroom.util.shared.IsConfig;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                        LambdaLogger.buildMessage("{} - [{}] - [{}] - [{}] - [{}]",
                                configProperty.getName(),
                                configProperty.getValue(),
                                configProperty.getDefaultValue(),
                                configProperty.getSource(),
                                configProperty.getDescription()))
                .collect(Collectors.joining("\n"));

        LOGGER.debug("Properties\n{}", txt);
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

    @Test
    void testGetGlobalProperties_defaultValueWithValue() throws IOException, ConfigurationException {

        AppConfig appConfig = getAppConfig();

        // simulate dropwiz setting a prop from the yaml
        String initialValue = appConfig.getRefDataStoreConfig().getLocalDir();
        String newValue = initialValue + "xxx";
        appConfig.getRefDataStoreConfig().setLocalDir(newValue);

        ConfigMapper configMapper = new ConfigMapper(appConfig);

        final Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        final ConfigProperty configProperty = configProperties.stream()
                .filter(confProp -> confProp.getName().equalsIgnoreCase("stroom.pipeline.refdata.localDir"))
                .findFirst()
                .orElseThrow();

        assertThat(configProperty.getValue()).isEqualTo(newValue);
        assertThat(configProperty.getDefaultValue()).isEqualTo(initialValue);
    }

    @Test
    void testGetGlobalProperties_defaultValueWithNullValue() throws IOException, ConfigurationException {

        AppConfig appConfig = getAppConfig();

        // simulate a prop not being defined in the yaml
        String initialValue = appConfig.getRefDataStoreConfig().getLocalDir();
        appConfig.getRefDataStoreConfig().setLocalDir(null);

        ConfigMapper configMapper = new ConfigMapper(appConfig);

        final Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        final ConfigProperty configProperty = configProperties.stream()
                .filter(confProp -> confProp.getName().equalsIgnoreCase("stroom.pipeline.refdata.localDir"))
                .findFirst()
                .orElseThrow();

        assertThat(configProperty.getValue()).isEqualTo(initialValue);
        assertThat(configProperty.getDefaultValue()).isEqualTo(initialValue);
    }

    @Test
    void update_string() throws IOException, ConfigurationException {
        AppConfig appConfig = getAppConfig();

        Supplier<String> getter = () -> appConfig.getCoreConfig().getTemp();
        String initialValue = getter.get();
        String newValue = initialValue + "/xxx";

        ConfigMapper configMapper = new ConfigMapper(appConfig);
        configMapper.updateConfigObject("stroom.core.temp", newValue);

        assertThat(getter.get()).isEqualTo(newValue);
    }

    @Test
    void update_boolean() throws IOException, ConfigurationException {
        AppConfig appConfig = getAppConfig();

        BooleanSupplier getter = () -> appConfig.getRefDataStoreConfig().isReadAheadEnabled();
        boolean initialValue = getter.getAsBoolean();
        boolean newValue = !initialValue;

        ConfigMapper configMapper = new ConfigMapper(appConfig);
        configMapper.updateConfigObject("stroom.pipeline.refdata.readAheadEnabled", Boolean.valueOf(newValue).toString().toLowerCase());

        assertThat(getter.getAsBoolean()).isEqualTo(newValue);
    }

    @Test
    void update_int() throws IOException, ConfigurationException {
        AppConfig appConfig = getAppConfig();

        IntSupplier getter = () -> appConfig.getRefDataStoreConfig().getMaxPutsBeforeCommit();
        int initialValue = getter.getAsInt();
        int newValue = initialValue + 1;

        ConfigMapper configMapper = new ConfigMapper(appConfig);
        configMapper.updateConfigObject("stroom.pipeline.refdata.maxPutsBeforeCommit", Integer.toString(newValue));

        assertThat(getter.getAsInt()).isEqualTo(newValue);
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    @Test
    void testGetGlobalProperties2() {
        TestConfig testConfig = new TestConfig();

        ConfigMapper configMapper = new ConfigMapper(testConfig);

        Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        configProperties.forEach(configProperty ->
                LOGGER.debug("{} - {}", configProperty.getName(), configProperty.getValue()));
    }


    @Test
    void update_string2() {
        TestConfig testConfig = new TestConfig();

        Supplier<String> getter = testConfig::getStringProp;
        String initialValue = getter.get();
        String newValue = initialValue + "xxx";

        ConfigMapper configMapper = new ConfigMapper(testConfig);
        configMapper.updateConfigObject("stroom.stringProp", newValue);

        assertThat(getter.get()).isEqualTo(newValue);
    }

    @Test
    void update_primitiveInt() {
        TestConfig testConfig = new TestConfig();

        IntSupplier getter = () -> testConfig.getTestPrimitiveConfig().getIntProp();
        int initialValue = getter.getAsInt();
        int newValue = initialValue + 1;

        ConfigMapper configMapper = new ConfigMapper(testConfig);
        configMapper.updateConfigObject("stroom.primitive.intProp", Integer.toString(newValue));

        assertThat(getter.getAsInt()).isEqualTo(newValue);
    }

    @Test
    void update_boxedInt() {
        TestConfig testConfig = new TestConfig();

        Supplier<Integer> getter = () -> testConfig.getTestBoxedConfig().getIntProp();
        Integer initialValue = getter.get();
        Integer newValue = initialValue + 1;

        ConfigMapper configMapper = new ConfigMapper(testConfig);
        configMapper.updateConfigObject("stroom.boxed.intProp", Integer.toString(newValue));

        assertThat(getter.get()).isEqualTo(newValue);
    }

    @Test
    void update_docRef() {
        TestConfig testConfig = new TestConfig();
        ConfigMapper configMapper = new ConfigMapper(testConfig);

        Supplier<DocRef> getter = testConfig::getDocRefProp;
        DocRef initialValue = getter.get();
        DocRef newValue = new DocRef.Builder()
                .type(initialValue.getType() + "xxx")
                .uuid(UUID.randomUUID().toString())
                .build();

        configMapper.updateConfigObject("stroom.docRefProp", ConfigMapper.convertToString(newValue));

        assertThat(getter.get()).isEqualTo(newValue);
    }

    @Test
    void update_enum() {
        TestConfig testConfig = new TestConfig();
        ConfigMapper configMapper = new ConfigMapper(testConfig);

        Supplier<TestConfig.State> getter = testConfig::getStateProp;
        TestConfig.State initialValue = getter.get();
        TestConfig.State newValue = TestConfig.State.ON;

        configMapper.updateConfigObject("stroom.stateProp", ConfigMapper.convertToString(newValue));

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

        configMapper.updateConfigObject("stroom.docRefListProp", ConfigMapper.convertToString(newValue));

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

        configMapper.updateConfigObject("stroom.stateListProp", ConfigMapper.convertToString(newValue));

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
        String newValueStr = ConfigMapper.convertToString(newValue);

        configMapper.updateConfigObject("stroom.stringListProp", newValueStr);

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

        String newValueStr = ConfigMapper.convertToString(newValue);

        configMapper.updateConfigObject("stroom.stringLongMapProp", newValueStr);

        assertThat(getter.get()).isEqualTo(newValue);
    }


    private AppConfig getAppConfig() throws IOException, ConfigurationException {
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

    private static class TestConfig implements IsConfig {

        private String stringProp = "initial value";
        private List<String> stringListProp = new ArrayList<>();
        private List<Integer> intListProp = new ArrayList<>();
        private Map<String, Long> stringLongMapProp = new HashMap<>();
        private DocRef docRefProp = new DocRef("MyType", UUID.randomUUID().toString(), "MyName");
        private List<DocRef> docRefListProp = List.of(
                new DocRef("MyType1", UUID.randomUUID().toString(), "MyDocRef1"),
                new DocRef("MyType2", UUID.randomUUID().toString(), "MyDocRef2"));
        private State stateProp = State.OFF;
        private List<State> stateListProp = List.of(State.ON, State.IN_BETWEEN);
        // sub-configs
        private TestPrimitiveConfig testPrimitiveConfig = new TestPrimitiveConfig();
        private TestBoxedConfig testBoxedConfig = new TestBoxedConfig();

        TestConfig() {
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

        String getStringProp() {
            return stringProp;
        }

        void setStringProp(final String stringValue) {
            this.stringProp = stringValue;
        }

        @JsonProperty("primitive")
        TestPrimitiveConfig getTestPrimitiveConfig() {
            return testPrimitiveConfig;
        }

        void setTestPrimitiveConfig(final TestPrimitiveConfig testPrimitiveConfig) {
            this.testPrimitiveConfig = testPrimitiveConfig;
        }

        @JsonProperty("boxed")
        TestBoxedConfig getTestBoxedConfig() {
            return testBoxedConfig;
        }

        void setTestBoxedConfig(final TestBoxedConfig testBoxedConfig) {
            this.testBoxedConfig = testBoxedConfig;
        }

        List<String> getStringListProp() {
            return stringListProp;
        }

        void setStringListProp(final List<String> stringListProp) {
            this.stringListProp = stringListProp;
        }

        List<Integer> getIntListProp() {
            return intListProp;
        }

        void setIntListProp(final List<Integer> intListProp) {
            this.intListProp = intListProp;
        }

        Map<String, Long> getStringLongMapProp() {
            return stringLongMapProp;
        }

        void setStringLongMapProp(final Map<String, Long> stringLongMapProp) {
            this.stringLongMapProp = stringLongMapProp;
        }

        DocRef getDocRefProp() {
            return docRefProp;
        }

        void setDocRefProp(final DocRef docRefProp) {
            this.docRefProp = docRefProp;
        }

        List<DocRef> getDocRefListProp() {
            return docRefListProp;
        }

        void setDocRefListProp(final List<DocRef> docRefListProp) {
            this.docRefListProp = docRefListProp;
        }

        State getStateProp() {
            return stateProp;
        }

        void setStateProp(final State stateProp) {
            this.stateProp = stateProp;
        }

        List<State> getStateListProp() {
            return stateListProp;
        }

        void setStateListProp(final List<State> stateListProp) {
            this.stateListProp = stateListProp;
        }

        public enum State {
            ON, IN_BETWEEN, OFF
        }
    }


    private static class TestPrimitiveConfig implements IsConfig {

        private boolean booleanProp = false;
        private int intProp = 123;
        private long longProp = 123L;
        private double doubleProp = 1.23;
        private short shortProp = 123;

        boolean isBooleanProp() {
            return booleanProp;
        }

        void setBooleanProp(final boolean booleanProp) {
            this.booleanProp = booleanProp;
        }

        int getIntProp() {
            return intProp;
        }

        void setIntProp(final int intProp) {
            this.intProp = intProp;
        }

        long getLongProp() {
            return longProp;
        }

        void setLongProp(final long longProp) {
            this.longProp = longProp;
        }

        double getDoubleProp() {
            return doubleProp;
        }

        void setDoubleProp(final double doubleProp) {
            this.doubleProp = doubleProp;
        }

        short getShortProp() {
            return shortProp;
        }

        void setShortProp(final short shortProp) {
            this.shortProp = shortProp;
        }
    }

    private static class TestBoxedConfig implements IsConfig {

        private Boolean booleanProp = false;
        private Integer intProp = 123;
        private Long longProp = 123L;
        private Double doubleProp = 1.23;
        private Short shortProp = 123;

        Boolean getBooleanProp() {
            return booleanProp;
        }

        void setBooleanProp(final Boolean booleanProp) {
            this.booleanProp = booleanProp;
        }

        Integer getIntProp() {
            return intProp;
        }

        void setIntProp(final Integer intProp) {
            this.intProp = intProp;
        }

        Long getLongProp() {
            return longProp;
        }

        void setLongProp(final Long longProp) {
            this.longProp = longProp;
        }

        Double getDoubleProp() {
            return doubleProp;
        }

        void setDoubleProp(final Double doubleProp) {
            this.doubleProp = doubleProp;
        }

        Short getShortProp() {
            return shortProp;
        }

        void setShortProp(final Short shortProp) {
            this.shortProp = shortProp;
        }
    }
}