/*
 * Copyright 2024 Crown Copyright
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

import stroom.activity.impl.db.ActivityConfig;
import stroom.analytics.impl.AnalyticsConfig;
import stroom.annotation.impl.AnnotationConfig;
import stroom.aws.s3.impl.S3Config;
import stroom.bytebuffer.ByteBufferPoolConfig;
import stroom.cluster.api.ClusterConfig;
import stroom.cluster.lock.impl.db.ClusterLockConfig;
import stroom.config.app.AppConfig;
import stroom.config.app.CrossModuleConfig;
import stroom.config.app.DataConfig;
import stroom.config.app.PropertyServiceConfig;
import stroom.config.app.SecurityConfig;
import stroom.config.app.SessionConfig;
import stroom.config.app.SessionCookieConfig;
import stroom.config.app.StatisticsConfig;
import stroom.config.common.CommonDbConfig;
import stroom.config.common.NodeUriConfig;
import stroom.config.common.PublicUriConfig;
import stroom.config.common.UiUriConfig;
import stroom.config.global.shared.ConfigProperty;
import stroom.config.global.shared.OverrideValue;
import stroom.contentstore.impl.ContentStoreConfig;
import stroom.core.receive.AutoContentCreationConfig;
import stroom.credentials.api.CredentialsConfig;
import stroom.dashboard.impl.DashboardConfig;
import stroom.docref.DocRef;
import stroom.docstore.impl.db.DocStoreConfig;
import stroom.event.logging.impl.LoggingConfig;
import stroom.explorer.impl.ExplorerConfig;
import stroom.feed.impl.FeedConfig;
import stroom.gitrepo.api.GitRepoConfig;
import stroom.importexport.impl.ContentPackImportConfig;
import stroom.importexport.impl.ExportConfig;
import stroom.index.impl.IndexConfig;
import stroom.index.impl.IndexFieldDbConfig;
import stroom.index.impl.selection.VolumeConfig;
import stroom.job.impl.JobSystemConfig;
import stroom.kafka.impl.KafkaConfig;
import stroom.langchain.api.ChatMemoryConfig;
import stroom.lifecycle.impl.LifecycleConfig;
import stroom.lmdb.LmdbConfig;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.node.impl.NodeConfig;
import stroom.pipeline.PipelineConfig;
import stroom.pipeline.refdata.ReferenceDataLmdbConfig;
import stroom.planb.impl.PlanBConfig;
import stroom.processor.impl.ProcessorConfig;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.rules.impl.StroomReceiptPolicyConfig;
import stroom.search.elastic.ElasticConfig;
import stroom.search.impl.SearchConfig;
import stroom.search.solr.SolrConfig;
import stroom.state.impl.StateConfig;
import stroom.storedquery.impl.StoredQueryConfig;
import stroom.ui.config.shared.UiConfig;
import stroom.util.config.PropertyUtil.Prop;
import stroom.util.io.ByteSize;
import stroom.util.io.StroomPathConfig;
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.PropertyPath;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.reflect.TypeToken;
import io.dropwizard.core.Configuration;
import io.vavr.Tuple;
import io.vavr.Tuple2;
import io.vavr.Tuple8;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestConfigMapper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestConfigMapper.class);

    @Test
    void getGlobalProperties() {

        final ConfigMapper configMapper = new ConfigMapper();

        final Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();


        //noinspection VariableTypeCanBeExplicit
        final var rows = configProperties.stream()
                .sorted(Comparator.comparing(ConfigProperty::getName))
                .map(configProperty ->
                        Tuple.of(
                                configProperty.getName().toString(),
                                configProperty.getDataTypeName(),
                                StringUtils.truncate(
                                        configProperty.getDefaultValue().orElse(""),
                                        0,
                                        50),
                                StringUtils.truncate(
                                        configProperty.getDatabaseOverrideValue().getValueOrElse(
                                                "UNSET", null),
                                        0,
                                        50),
                                StringUtils.truncate(
                                        configProperty.getYamlOverrideValue().getValueOrElse(
                                                "UNSET", null),
                                        0,
                                        50),
                                StringUtils.truncate(
                                        configProperty.getEffectiveValue().orElse(""),
                                        0,
                                        50),
                                configProperty.getSource().getName(),
                                StringUtils.truncate(configProperty.getDescription(), 0, 100)))
                .collect(Collectors.toList());

        final String asciiTable = AsciiTable.builder(rows)
                .withColumn(Column.of("Property Path", Tuple8::_1))
                .withColumn(Column.of("Type", Tuple8::_2))
                .withColumn(Column.of("Default Value", Tuple8::_3))
                .withColumn(Column.of("DB Override Val", Tuple8::_4))
                .withColumn(Column.of("Yaml Override Val", Tuple8::_5))
                .withColumn(Column.of("Effective Val", Tuple8::_6))
                .withColumn(Column.of("Source", Tuple8::_7))
                .withColumn(Column.of("Description", Tuple8::_8))
                .build();

        LOGGER.debug("Properties\n{}", asciiTable);
    }

    @Test
    void getDataTypeNames() {
        final ConfigMapper configMapper = new ConfigMapper();

        final Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        final List<Tuple2<String, String>> rows = configProperties.stream()
                .sorted(Comparator.comparing(ConfigProperty::getName))
                .map(configProperty ->
                        Tuple.of(configProperty.getName().toString(), configProperty.getDataTypeName()))
                .collect(Collectors.toList());

        final String asciiTable = AsciiTable.builder(rows)
                .withColumn(Column.of("Property Path", Tuple2::_1))
                .withColumn(Column.of("Data Type", Tuple2::_2))
                .build();

        LOGGER.debug("Properties\n{}", asciiTable);
    }

    @Test
    void getUniqueDataTypes() {
        final ConfigMapper configMapper = new ConfigMapper();

        final Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        final Map<String, String> map = configProperties.stream()
                .map(configProp ->
                        Tuple.of(configMapper.getProp(configProp.getName())
                                        .get()
                                        .getValueClass().getSimpleName(),
                                configProp))
                .collect(Collectors.groupingBy(
                        tuple2 -> tuple2._1(),
                        Collectors.mapping(
                                tuple2 -> tuple2._2().getName().toString(),
                                Collectors.joining(", "))));

        final List<Tuple2<String, String>> rows = map
                .entrySet()
                .stream()
                .map(entry -> Tuple.of(entry.getKey(), entry.getValue()))
                .sorted()
                .collect(Collectors.toList());

        final String asciiTable = AsciiTable.builder(rows)
                .withColumn(Column.of("Data Type", Tuple2::_1))
                .withColumn(Column.of("Property Paths", Tuple2::_2))
                .build();

        LOGGER.debug("Properties\n{}", asciiTable);
    }

    @Test
    void testSerdeAllProperties() {

        final TestConfig testConfig = new TestConfig();
        final ConfigMapper configMapper = new ConfigMapper(testConfig, TestConfig::new);

        final Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        // getting each prop as a ConfigProperty ensure we can serialise to string
        configProperties.forEach(configProperty -> {
            configProperty.setDatabaseOverrideValue(configProperty.getDefaultValue().orElse(null));

            // verify we can convert back to an object from a string
            final ConfigProperty newConfigProperty = configMapper.decorateDbConfigProperty(configProperty);

            LOGGER.debug(configProperty.toString());
            assertThat(newConfigProperty.getSource())
                    .isIn(ConfigProperty.SourceType.DATABASE, ConfigProperty.SourceType.DEFAULT);
        });
    }


    @Test
    void testValidatePropertyPath_valid() {
        final ConfigMapper configMapper = new ConfigMapper();

        final boolean isValid = configMapper.validatePropertyPath(PropertyPath.fromPathString("stroom.ui.aboutHtml"));

        assertThat(isValid).isTrue();
    }

    @Test
    void testValidatePropertyPath_invalid() {
        final AppConfig appConfig = new AppConfig();
        final ConfigMapper configMapper = new ConfigMapper();

        final boolean isValid = configMapper.validatePropertyPath(PropertyPath.fromPathString("stroom.unknown.prop"));

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
    void testGetGlobalProperties_defaultValueWithValue() {

        final AppConfig appConfig = getAppConfig();

        // simulate dropwiz setting a prop from the yaml
        final ReferenceDataLmdbConfig referenceDataLmdbConfig = appConfig.getPipelineConfig()
                .getReferenceDataConfig()
                .getLmdbConfig();

        final String initialValue = referenceDataLmdbConfig.getLocalDir();
        final String newValue = initialValue + "xxx";
        referenceDataLmdbConfig.setLocalDir(newValue);

        final ConfigMapper configMapper = new ConfigMapper(appConfig);
//        configMapper.updateConfigFromYaml();

        final Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        final ConfigProperty configProperty = configProperties.stream()
                .filter(confProp ->
                        confProp.getName().equalsIgnoreCase(PropertyPath.fromPathString(
                                referenceDataLmdbConfig.getFullPathStr(LmdbConfig.LOCAL_DIR_PROP_NAME))))
                .findFirst()
                .orElseThrow();

        assertValues(configProperty,
                initialValue,
                OverrideValue.unSet(String.class),
                OverrideValue.with(newValue),
                newValue);
    }

    @Test
    void testGetGlobalProperties_defaultValueWithNullValue() {
        final AppConfig appConfig = getAppConfig();

        // simulate a prop not being defined in the yaml
        final ReferenceDataLmdbConfig lmdbConfig = appConfig.getPipelineConfig()
                .getReferenceDataConfig()
                .getLmdbConfig();
        final String initialValue = lmdbConfig.getLocalDir();
        final String newYamlValue = null;
        lmdbConfig.setLocalDir(newYamlValue);

        final ConfigMapper configMapper = new ConfigMapper();
        configMapper.refreshGlobalPropYamlOverrides(appConfig);

        final Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        final ConfigProperty configProperty = configProperties.stream()
                .filter(confProp -> confProp.getName().equalsIgnoreCase(PropertyPath.fromPathString(
                        lmdbConfig.getFullPathStr(LmdbConfig.LOCAL_DIR_PROP_NAME))))
                .findFirst()
                .orElseThrow();

        assertValues(
                configProperty,
                initialValue,
                OverrideValue.unSet(String.class),
                OverrideValue.with(newYamlValue),
                newYamlValue);

//        assertThat(configProperty.getValue()).isEqualTo(initialValue);
//        assertThat(configProperty.getDefaultValue()).isEqualTo(initialValue);
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    @Test
    void testGetGlobalProperties2() {
        final TestConfig testConfig = new TestConfig();

        final ConfigMapper configMapper = new ConfigMapper(testConfig, TestConfig::new);

        final Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        configProperties.forEach(configProperty ->
                LOGGER.debug("{} - {}", configProperty.getName(), configProperty.getEffectiveValue()
                        .orElse(null)));
    }

    @Test
    void testFindPropsWithNoDefault() {
        final ConfigMapper configMapper = new ConfigMapper();

        final Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        LOGGER.info("Properties with no default value");
        configProperties.forEach(configProperty -> {
            if (configProperty.getDefaultValue().isEmpty()
                && !configProperty.getName().toString().contains("Pool")
                && !configProperty.getName().toString().contains("expireAfterWrite")
                && !configProperty.getName().toString().contains("expireAfterAccess")
                && !configProperty.getName().toString().contains("db.connection")
                && !configProperty.getName().toString().contains("Uri.")) {
                LOGGER.info("{}", configProperty.getName());
            }
        });
    }

    @Test
    void testFindPropsWithNoDescription() {
        final ConfigMapper configMapper = new ConfigMapper();

        final Collection<ConfigProperty> configProperties = configMapper.getGlobalProperties();

        LOGGER.info("Properties with no description");
        final List<String> propsWithNoDesc = configProperties.stream()
                .filter(configProperty ->
                        configProperty.getDescription() == null || configProperty.getDescription().isEmpty())
                .map(ConfigProperty::getName)
                .map(PropertyPath::toString)
                .sorted()
                .peek(name ->
                        LOGGER.info("{}", name))
                .collect(Collectors.toList());

        Assertions.assertThat(propsWithNoDesc)
                .isEmpty();
    }

    @Test
    void updateValues() {
        doUpdateValueTest(
                "stroom.primitive.booleanProp",
                tc -> tc.getTestPrimitiveConfig().isBooleanProp(),
                "true",
                Boolean::valueOf);

        doUpdateValueTest(
                "stroom.boxed.booleanProp",
                tc -> tc.getTestBoxedConfig().getBooleanProp(),
                "true",
                Boolean::valueOf);

        doUpdateValueTest(
                "stroom.primitive.intProp",
                tc -> tc.getTestPrimitiveConfig().getIntProp(),
                "999",
                Integer::parseInt);

        doUpdateValueTest(
                "stroom.boxed.intProp",
                tc -> tc.getTestBoxedConfig().getIntProp(),
                "999",
                Integer::parseInt);

        doUpdateValueTest(
                "stroom.primitive.longProp",
                tc -> tc.getTestPrimitiveConfig().getLongProp(),
                "999",
                Long::parseLong);

        doUpdateValueTest(
                "stroom.boxed.longProp",
                tc -> tc.getTestBoxedConfig().getLongProp(),
                "999",
                Long::parseLong);

        doUpdateValueTest(
                "stroom.stringProp",
                TestConfig::getStringProp,
                "yyyyyy",
                Function.identity());

        doUpdateValueTest(
                "stroom.stroomDurationProp",
                TestConfig::getStroomDurationProp,
                "P1DT6H",
                StroomDuration::parse);

        doUpdateValueTest(
                "stroom.byteSizeProp",
                TestConfig::getByteSizeProp,
                "1MiB",
                ByteSize::parse);

        doUpdateValueTestComplex(
                "stroom.docRefProp",
                TestConfig::getDocRefProp,
                ",docRef(aaaaaa,bbbbbbb,ccccccc)",
                (prop, str) -> ConfigMapper.convertToObject(prop, str, DocRef.class));

        final Type stateListType = new TypeToken<List<TestConfig.State>>() {
        }.getType();

        doUpdateValueTestComplex(
                "stroom.stateListProp",
                TestConfig::getStateListProp,
                ",ON,OFF",
                (prop, str) ->
                        ConfigMapper.convertToObject(prop, str, stateListType));

        doUpdateValueTest(
                "stroom.stateProp",
                TestConfig::getStateProp,
                "ON",
                TestConfig.State::valueOf);
    }

    <T> void doUpdateValueTest(final String path,
                               final Function<TestConfig, T> getter,
                               final String newValueAsStr,
                               final Function<String, T> parseFunc) {
        doUpdateValueTestComplex(
                path,
                getter,
                newValueAsStr,
                (prop, s) -> parseFunc.apply(s));
    }

    <T> void doUpdateValueTestComplex(final String path,
                                      final Function<TestConfig, T> getter,
                                      final String newValueAsStr,
                                      final BiFunction<Prop, String, T> parseFunc) {

        LOGGER.info("Testing {}, with new value {}", path, newValueAsStr);

        final TestConfig testConfig = new TestConfig();

        final T originalObj = getter.apply(testConfig);

        final PropertyPath fullPath = PropertyPath.fromPathString(path);

        final ConfigMapper configMapper = new ConfigMapper(testConfig, TestConfig::new);

        final Prop prop = configMapper.getProp(fullPath)
                .orElseThrow();

        final boolean isValidPath = configMapper.validatePropertyPath(PropertyPath.fromPathString(path));

        assertThat(isValidPath).isTrue();

        final ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath)
                .orElseThrow();

        final ConfigProperty configPropertyCopy = copyConfigProperty(configProperty);

        // make sure our new value differs from the current one
        assertThat(configPropertyCopy.getDefaultValue().get()).isNotEqualTo(newValueAsStr);

        configPropertyCopy.setDatabaseOverrideValue(newValueAsStr);
        configMapper.decorateDbConfigProperty(configPropertyCopy);

        final T newObj = parseFunc.apply(prop, newValueAsStr);

        LOGGER.info("{} - {} => {}", newObj.getClass().getSimpleName(), originalObj, newObj);

        assertThat(newObj).isNotEqualTo(originalObj);

        final TestConfig newTestConfig = configMapper.getConfigObject(TestConfig.class);

        // make sure the db override value has made it into the config obj
        assertThat(getter.apply(newTestConfig))
                .isEqualTo(newObj);
    }

    @Test
    void update_docRefList() {
        final TestConfig testConfig = new TestConfig();
        final ConfigMapper configMapper = new ConfigMapper(testConfig, TestConfig::new);

        final List<DocRef> initialValue = testConfig.getDocRefListProp();
        final List<DocRef> newValue = new ArrayList<>();
        initialValue.forEach(docRef ->
                newValue.add(DocRef.builder()
                        .type(docRef.getType() + "x")
                        .uuid(UUID.randomUUID().toString())
                        .name(docRef.getName() + "xx")
                        .build()));
        newValue.add(DocRef.builder()
                .type("NewDocRefType")
                .uuid(UUID.randomUUID().toString())
                .build());
        final PropertyPath fullPath = PropertyPath.fromPathString("stroom.docRefListProp");

        final ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        // Make a copy as decorateDbConfigProperty will be comparing the one from the map
        // against this one.
        final ConfigProperty configPropertyCopy = copyConfigProperty(configProperty);
        configPropertyCopy.setDatabaseOverrideValue(ConfigMapper.convertToString(newValue));
        configMapper.decorateDbConfigProperty(configPropertyCopy);

        final TestConfig newTestConfig = configMapper.getConfigObject(TestConfig.class);

        assertThat(newTestConfig.getDocRefListProp()).isEqualTo(newValue);
    }

    @Test
    void update_enumList() {
        final TestConfig testConfig = new TestConfig();
        final ConfigMapper configMapper = new ConfigMapper(testConfig, TestConfig::new);

        final List<TestConfig.State> initialValue = testConfig.getStateListProp();
        final List<TestConfig.State> newValue = new ArrayList<>(initialValue);
        newValue.add(TestConfig.State.ON);
        final PropertyPath fullPath = PropertyPath.fromPathString("stroom.stateListProp");

        final ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        // Make a copy as decorateDbConfigProperty will be comparing the one from the map
        // against this one.
        final ConfigProperty configPropertyCopy = copyConfigProperty(configProperty);
        configPropertyCopy.setDatabaseOverrideValue(ConfigMapper.convertToString(newValue));
        configMapper.decorateDbConfigProperty(configPropertyCopy);

        final TestConfig newTestConfig = configMapper.getConfigObject(TestConfig.class);

        assertThat(newTestConfig.getStateListProp()).isEqualTo(newValue);
    }

    @Test
    void update_stringList() {
        final TestConfig testConfig = new TestConfig();
        final ConfigMapper configMapper = new ConfigMapper(testConfig, TestConfig::new);

        final List<String> initialValue = testConfig.getStringListProp();
        final List<String> newValue = Stream.of(initialValue, initialValue)
                .flatMap(List::stream)
                .map(str -> str + "x")
                .collect(Collectors.toList());
        final PropertyPath fullPath = PropertyPath.fromPathString("stroom.stringListProp");

        final ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        // Make a copy as decorateDbConfigProperty will be comparing the one from the map
        // against this one.
        final ConfigProperty configPropertyCopy = copyConfigProperty(configProperty);
        configPropertyCopy.setDatabaseOverrideValue(ConfigMapper.convertToString(newValue));
        configMapper.decorateDbConfigProperty(configPropertyCopy);

        final TestConfig newtTestConfig = configMapper.getConfigObject(TestConfig.class);

        assertThat(newtTestConfig.getStringListProp())
                .isEqualTo(newValue);
    }

    @Test
    void update_stringLongMap() {
        final TestConfig testConfig = new TestConfig();
        final ConfigMapper configMapper = new ConfigMapper(testConfig, TestConfig::new);

        final Supplier<Map<String, Long>> getter = testConfig::getStringLongMapProp;
        final Map<String, Long> initialValue = getter.get();
        final Map<String, Long> newValue = new HashMap<>();
        initialValue.forEach((k, v) ->
                newValue.put(k, v + 10));
        newValue.put("k4", 14L);
        final PropertyPath fullPath = PropertyPath.fromPathString("stroom.stringLongMapProp");

        final ConfigProperty configProperty = configMapper.getGlobalProperty(fullPath).orElseThrow();
        // Make a copy as decorateDbConfigProperty will be comparing the one from the map
        // against this one.
        final ConfigProperty configPropertyCopy = copyConfigProperty(configProperty);
        configPropertyCopy.setDatabaseOverrideValue(ConfigMapper.convertToString(newValue));
        configMapper.decorateDbConfigProperty(configPropertyCopy);

        final TestConfig newTestConfig = configMapper.getConfigObject(TestConfig.class);
        assertThat(newTestConfig.getStringLongMapProp()).isEqualTo(newValue);
    }

    @Test
    void testPrecedenceDefaultOnly() {
        final TestConfig testConfig = new TestConfig();
        final String defaultValue = testConfig.getStringProp();

        final ConfigMapper configMapper = new ConfigMapper(testConfig, TestConfig::new);

        final ConfigProperty configProperty = configMapper
                .getGlobalProperty(PropertyPath.fromPathString("stroom.stringProp"))
                .orElseThrow();

        assertThat(configProperty.getDefaultValue().orElseThrow())
                .isEqualTo(defaultValue);
        assertThat(configProperty.getDatabaseOverrideValue().isHasOverride())
                .isFalse();
        assertThat(configProperty.getYamlOverrideValue().isHasOverride())
                .isFalse();
        assertThat(configProperty.getEffectiveValue().orElseThrow())
                .isEqualTo(defaultValue);
    }


    @Test
    void testValidateDelimiter_bad() {
        Assertions.assertThatThrownBy(() ->
                        ConfigMapper.validateDelimiter(
                                "xxxx",
                                0,
                                "first",
                                "dummy example"))
                .isInstanceOf(RuntimeException.class);
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
    void testValidateStringValue_set_good1() {
        doValidateStringValueTest("stroom.stringSetProp", "|item1|item2|item3", true);
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
        doValidateStringValueTest("stroom.docRefListProp",
                ",|docRef(type1|uuid1|name1),|docRef(type1|uuid1|name1)",
                true);
    }

    @Test
    void testValidateStringValue_docRefList_bad() {
        // $ not valid delimiter
        doValidateStringValueTest("stroom.docRefListProp",
                ",$docRef(type1$uuid1$name1),$docRef(type1$uuid1$name1)",
                false);
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
    void testValidateStringValue_stroomDuration_good() {
        doValidateStringValueTest("stroom.stroomDurationProp", "P1DT1M", true);
    }

    @Test
    void testValidateStringValue_stroomDuration_bad() {
        doValidateStringValueTest("stroom.stroomDurationProp", "xxxxx", false);
    }

    @Test
    void testValidateStringValue_byteSize_good() {
        doValidateStringValueTest("stroom.byteSizeProp", "5KiB", true);
    }

    @Test
    void testValidateStringValue_byteSize_bad() {
        doValidateStringValueTest("stroom.byteSizeProp", "xxxxx", false);
    }

//    @Test
//    void testBuildAppConfigFromFile() {
//        final Path configFile = Path.of("../../stroom-app/dev.yml");
//        assertThat(configFile)
//                .isRegularFile();
//        final AppConfig appConfig = ConfigMapper.buildMergedAppConfig(configFile);
//        assertThat(appConfig)
//                .isNotNull();
//    }

    //    @Test
//    void testRefreshConfig() {
//
//        final AppConfig appConfig = new AppConfig();
//        ConfigMapper configMapper = new ConfigMapper(appConfig);
//
//        configMapper.refreshConfig(appConfig);
//    }

    private void doValidateStringValueTest(final String path, final String value, final boolean shouldValidate) {
        final TestConfig testConfig = new TestConfig();
        final ConfigMapper configMapper = new ConfigMapper(testConfig, TestConfig::new);
        final PropertyPath propertyPath = PropertyPath.fromPathString(path);

        if (shouldValidate) {
            configMapper.validateValueSerialisation(propertyPath, value);
        } else {
            // no leading delimiter
            Assertions.assertThatThrownBy(() ->
                            configMapper.validateValueSerialisation(propertyPath, value))
                    .isInstanceOf(RuntimeException.class);
        }
    }

    private AppConfig getAppConfig() {
        return new AppConfig();
    }

    private ConfigProperty copyConfigProperty(final ConfigProperty configProperty) {
        final ConfigProperty newConfigProperty = new ConfigProperty(
                configProperty.getId(),
                configProperty.getVersion(),
                configProperty.getCreateTimeMs(),
                configProperty.getCreateUser(),
                configProperty.getUpdateTimeMs(),
                configProperty.getUpdateUser(),
                configProperty.getName(),
                configProperty.getDefaultValue().orElse(null),
                configProperty.getDatabaseOverrideValue(),
                configProperty.getYamlOverrideValue(),
                configProperty.getDescription(),
                configProperty.isEditable(),
                configProperty.isPassword(),
                configProperty.isRequireRestart(),
                configProperty.isRequireUiRestart(),
                configProperty.getDataTypeName());

        return newConfigProperty;
    }

//    private AppConfig getDevYamlAppConfig() throws IOException, ConfigurationException {
//        ConfigurationSourceProvider configurationSourceProvider = new SubstitutingSourceProvider(
//                new FileConfigurationSourceProvider(),
//                new EnvironmentVariableSubstitutor(false));
//
//        ConfigurationFactoryFactory<Config> configurationFactoryFactory = new DefaultConfigurationFactoryFactory<>();
//
//        final ConfigurationFactory<Config> configurationFactory = configurationFactoryFactory
//                .create(
//                        Config.class,
//                        io.dropwizard.jersey.validation.Validators.newValidator(),
//                        Jackson.newObjectMapper(),
//                        "dw");
//        Config config = configurationFactory.build(configurationSourceProvider, "../../stroom-app/dev.yml");
//
//        return config.getAppConfig();
//    }


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

        private final String stringProp;
        private final List<String> stringListProp;
        private final List<Integer> intListProp;
        private final Set<String> stringSetProp;
        private final Map<String, Long> stringLongMapProp;
        private final DocRef docRefProp;
        private final List<DocRef> docRefListProp;
        private final State stateProp;
        private final List<State> stateListProp;
        private final Path pathProp;
        private final StroomDuration stroomDurationProp;
        private final ByteSize byteSizeProp;

        // sub-configs
        private final TestPrimitiveConfig testPrimitiveConfig;
        private final TestBoxedConfig testBoxedConfig;

        public TestConfig() {
            super();

            stringProp = "initial value";
            stringListProp = List.of("item 1", "item 2", "item 3");
            intListProp = List.of(1, 2, 3);
            stringSetProp = Set.of("A", "B", "C");
            stringLongMapProp = Map.of(
                    "k1", 1L,
                    "k2", 2L,
                    "k3", 3L);
            docRefProp = new DocRef("MyType", "9d9ff899-c6db-46c1-97bf-a8015a853b38", "MyName");
            docRefListProp = List.of(
                    new DocRef("MyType1", "9457f9ff-eb2a-4ef1-b60d-769e9a987cd2", "MyDocRef1"),
                    new DocRef("MyType2", "56068221-1a7d-486c-9fa7-af8b98733e53", "MyDocRef2"));
            stateProp = State.OFF;
            stateListProp = List.of(State.ON, State.IN_BETWEEN);
            pathProp = Path.of("/a/b/c/d");
            stroomDurationProp = StroomDuration.ofMinutes(5);
            byteSizeProp = ByteSize.ofKibibytes(2);

            // sub-configs
            testPrimitiveConfig = new TestPrimitiveConfig();
            testBoxedConfig = new TestBoxedConfig();
        }


        @JsonCreator
        @SuppressWarnings("checkstyle:LineLength")
        public TestConfig(
                @JsonProperty(PROP_NAME_HALT_BOOT_ON_CONFIG_VALIDATION_FAILURE) final boolean haltBootOnConfigValidationFailure,
                @JsonProperty(CrossModuleConfig.NAME) final CrossModuleConfig crossModuleConfig,
                @JsonProperty(PROP_NAME_ACTIVITY) final ActivityConfig activityConfig,
                @JsonProperty(PROP_NAME_ANALYTICS) final AnalyticsConfig analyticsConfig,
                @JsonProperty(PROP_NAME_ANNOTATION) final AnnotationConfig annotationConfig,
                @JsonProperty(PROP_NAME_CONTENT_STORE) final ContentStoreConfig appStoreConfig,
                @JsonProperty(PROP_NAME_AUTO_CONTENT_CREATION) final AutoContentCreationConfig autoContentCreationConfig,
                @JsonProperty(PROP_NAME_BYTE_BUFFER_POOL) final ByteBufferPoolConfig byteBufferPoolConfig,
                @JsonProperty(PROP_NAME_CHAT_MEMORY) final ChatMemoryConfig chatMemoryConfig,
                @JsonProperty(PROP_NAME_CLUSTER) final ClusterConfig clusterConfig,
                @JsonProperty(PROP_NAME_CLUSTER_LOCK) final ClusterLockConfig clusterLockConfig,
                @JsonProperty(PROP_NAME_COMMON_DB_DETAILS) final CommonDbConfig commonDbConfig,
                @JsonProperty(PROP_NAME_CONTENT_PACK_IMPORT) final ContentPackImportConfig contentPackImportConfig,
                @JsonProperty(PROP_NAME_CREDENTIALS) final CredentialsConfig credentialsConfig,
                @JsonProperty(PROP_NAME_DASHBOARD) final DashboardConfig dashboardConfig,
                @JsonProperty(PROP_NAME_DATA) final DataConfig dataConfig,
                @JsonProperty(PROP_NAME_DOCSTORE) final DocStoreConfig docStoreConfig,
                @JsonProperty(PROP_NAME_ELASTIC) final ElasticConfig elasticConfig,
                @JsonProperty(PROP_NAME_EXPLORER) final ExplorerConfig explorerConfig,
                @JsonProperty(PROP_NAME_EXPORT) final ExportConfig exportConfig,
                @JsonProperty(PROP_NAME_FEED) final FeedConfig feedConfig,
                @JsonProperty(PROP_NAME_GIT_REPO) final GitRepoConfig gitRepoConfig,
                @JsonProperty(PROP_NAME_INDEX) final IndexConfig indexConfig,
                @JsonProperty(PROP_NAME_JOB) final JobSystemConfig jobSystemConfig,
                @JsonProperty(PROP_NAME_KAFKA) final KafkaConfig kafkaConfig,
                @JsonProperty(PROP_NAME_LIFECYCLE) final LifecycleConfig lifecycleConfig,
                @JsonProperty(PROP_NAME_LMDB_LIBRARY) final LmdbLibraryConfig lmdbLibraryConfig,
                @JsonProperty(PROP_NAME_LOGGING) final LoggingConfig loggingConfig,
                @JsonProperty(PROP_NAME_NODE) final NodeConfig nodeConfig,
                @JsonProperty(PROP_NAME_NODE_URI) final NodeUriConfig nodeUri,
                @JsonProperty(PROP_NAME_PIPELINE) final PipelineConfig pipelineConfig,
                @JsonProperty(PROP_NAME_PROCESSOR) final ProcessorConfig processorConfig,
                @JsonProperty(PROP_NAME_PROPERTIES) final PropertyServiceConfig propertyServiceConfig,
                @JsonProperty(PROP_NAME_PUBLIC_URI) final PublicUriConfig publicUri,
                @JsonProperty(PROP_NAME_QUERY_DATASOURCE) final IndexFieldDbConfig queryDataSourceConfig,
                @JsonProperty(PROP_NAME_RECEIVE) final ReceiveDataConfig receiveDataConfig,
                @JsonProperty(PROP_NAME_RECEIPT_POLICY) final StroomReceiptPolicyConfig receiptPolicyConfig,
                @JsonProperty(PROP_NAME_S3) final S3Config s3Config,
                @JsonProperty(PROP_NAME_SEARCH) final SearchConfig searchConfig,
                @JsonProperty(PROP_NAME_SECURITY) final SecurityConfig securityConfig,
                @JsonProperty(PROP_NAME_SESSION_COOKIE) final SessionCookieConfig sessionCookieConfig,
                @JsonProperty(PROP_NAME_SESSION) final SessionConfig sessionConfig,
                @JsonProperty(PROP_NAME_SOLR) final SolrConfig solrConfig,
                @JsonProperty(PROP_NAME_STATE) final StateConfig stateConfig,
                @JsonProperty(PROP_NAME_PLANB) final PlanBConfig planBConfig,
                @JsonProperty(PROP_NAME_STATISTICS) final StatisticsConfig statisticsConfig,
                @JsonProperty(PROP_NAME_QUERY_HISTORY) final StoredQueryConfig storedQueryConfig,
                @JsonProperty(PROP_NAME_PATH) final StroomPathConfig pathConfig,
                @JsonProperty(PROP_NAME_UI) final UiConfig uiConfig,
                @JsonProperty(PROP_NAME_UI_URI) final UiUriConfig uiUri,
                @JsonProperty(PROP_NAME_VOLUMES) final VolumeConfig volumeConfig,
                @JsonProperty("stringProp") final String stringProp,
                @JsonProperty("stringListProp") final List<String> stringListProp,
                @JsonProperty("intListProp") final List<Integer> intListProp,
                @JsonProperty("stringSetProp") final Set<String> stringSetProp,
                @JsonProperty("stringLongMapProp") final Map<String, Long> stringLongMapProp,
                @JsonProperty("docRefProp") final DocRef docRefProp,
                @JsonProperty("docRefListProp") final List<DocRef> docRefListProp,
                @JsonProperty("stateProp") final State stateProp,
                @JsonProperty("stateListProp") final List<State> stateListProp,
                @JsonProperty("pathProp") final Path pathProp,
                @JsonProperty("stroomDurationProp") final StroomDuration stroomDurationProp,
                @JsonProperty("byteSizeProp") final ByteSize byteSizeProp,
                @JsonProperty("primitive") final TestPrimitiveConfig testPrimitiveConfig,
                @JsonProperty("boxed") final TestBoxedConfig testBoxedConfig) {

            super(haltBootOnConfigValidationFailure,
                    crossModuleConfig,
                    activityConfig,
                    analyticsConfig,
                    annotationConfig,
                    appStoreConfig,
                    autoContentCreationConfig,
                    byteBufferPoolConfig,
                    chatMemoryConfig,
                    clusterConfig,
                    clusterLockConfig,
                    commonDbConfig,
                    contentPackImportConfig,
                    credentialsConfig,
                    dashboardConfig,
                    dataConfig,
                    docStoreConfig,
                    elasticConfig,
                    explorerConfig,
                    exportConfig,
                    feedConfig,
                    gitRepoConfig,
                    indexConfig,
                    jobSystemConfig,
                    kafkaConfig,
                    lifecycleConfig,
                    lmdbLibraryConfig,
                    loggingConfig,
                    nodeConfig,
                    nodeUri,
                    pipelineConfig,
                    processorConfig,
                    propertyServiceConfig,
                    publicUri,
                    queryDataSourceConfig,
                    receiveDataConfig,
                    receiptPolicyConfig,
                    s3Config,
                    searchConfig,
                    securityConfig,
                    sessionCookieConfig,
                    sessionConfig,
                    solrConfig,
                    stateConfig,
                    planBConfig,
                    statisticsConfig,
                    storedQueryConfig,
                    pathConfig,
                    uiConfig,
                    uiUri,
                    volumeConfig);

            this.stringProp = stringProp;
            this.stringListProp = stringListProp;
            this.intListProp = intListProp;
            this.stringSetProp = stringSetProp;
            this.stringLongMapProp = stringLongMapProp;
            this.docRefProp = docRefProp;
            this.docRefListProp = docRefListProp;
            this.stateProp = stateProp;
            this.stateListProp = stateListProp;
            this.pathProp = pathProp;
            this.stroomDurationProp = stroomDurationProp;
            this.byteSizeProp = byteSizeProp;
            this.testPrimitiveConfig = testPrimitiveConfig;
            this.testBoxedConfig = testBoxedConfig;
        }

        public String getStringProp() {
            return stringProp;
        }

        @JsonProperty("primitive")
        public TestPrimitiveConfig getTestPrimitiveConfig() {
            return testPrimitiveConfig;
        }

        @JsonProperty("boxed")
        public TestBoxedConfig getTestBoxedConfig() {
            return testBoxedConfig;
        }

        public List<String> getStringListProp() {
            return stringListProp;
        }

        public List<Integer> getIntListProp() {
            return intListProp;
        }

        public Set<String> getStringSetProp() {
            return stringSetProp;
        }

        public Map<String, Long> getStringLongMapProp() {
            return stringLongMapProp;
        }

        public DocRef getDocRefProp() {
            return docRefProp;
        }

        public List<DocRef> getDocRefListProp() {
            return docRefListProp;
        }

        public State getStateProp() {
            return stateProp;
        }

        public List<State> getStateListProp() {
            return stateListProp;
        }

        public Path getPathProp() {
            return pathProp;
        }

        public StroomDuration getStroomDurationProp() {
            return stroomDurationProp;
        }

        public ByteSize getByteSizeProp() {
            return byteSizeProp;
        }

        public enum State {
            ON,
            IN_BETWEEN,
            OFF
        }
    }


    public static class TestPrimitiveConfig extends AbstractConfig {

        private final boolean booleanProp;
        private final int intProp;
        private final long longProp;
        private final double doubleProp;
        private final short shortProp;

        public TestPrimitiveConfig() {
            booleanProp = false;
            intProp = 123;
            longProp = 123L;
            doubleProp = 1.23;
            shortProp = 123;
        }

        @JsonCreator
        public TestPrimitiveConfig(@JsonProperty("booleanProp") final boolean booleanProp,
                                   @JsonProperty("intProp") final int intProp,
                                   @JsonProperty("longProp") final long longProp,
                                   @JsonProperty("doubleProp") final double doubleProp,
                                   @JsonProperty("shortProp") final short shortProp) {
            this.booleanProp = booleanProp;
            this.intProp = intProp;
            this.longProp = longProp;
            this.doubleProp = doubleProp;
            this.shortProp = shortProp;
        }

        public boolean isBooleanProp() {
            return booleanProp;
        }

        public int getIntProp() {
            return intProp;
        }

        public long getLongProp() {
            return longProp;
        }

        public double getDoubleProp() {
            return doubleProp;
        }

        public short getShortProp() {
            return shortProp;
        }
    }

    public static class TestBoxedConfig extends AbstractConfig {

        private final Boolean booleanProp;
        private final Integer intProp;
        private final Long longProp;
        private final Double doubleProp;
        private final Short shortProp;

        public TestBoxedConfig() {
            booleanProp = false;
            intProp = 123;
            longProp = 123L;
            doubleProp = 1.23;
            shortProp = 123;
        }

        @JsonCreator
        public TestBoxedConfig(@JsonProperty("booleanProp") final Boolean booleanProp,
                               @JsonProperty("intProp") final Integer intProp,
                               @JsonProperty("longProp") final Long longProp,
                               @JsonProperty("doubleProp") final Double doubleProp,
                               @JsonProperty("shortProp") final Short shortProp) {
            this.booleanProp = booleanProp;
            this.intProp = intProp;
            this.longProp = longProp;
            this.doubleProp = doubleProp;
            this.shortProp = shortProp;
        }

        public Boolean getBooleanProp() {
            return booleanProp;
        }

        public Integer getIntProp() {
            return intProp;
        }

        public Long getLongProp() {
            return longProp;
        }

        public Double getDoubleProp() {
            return doubleProp;
        }

        public Short getShortProp() {
            return shortProp;
        }
    }
}
