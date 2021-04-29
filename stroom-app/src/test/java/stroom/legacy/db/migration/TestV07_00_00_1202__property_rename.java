package stroom.legacy.db.migration;

import stroom.config.app.AppConfig;
import stroom.config.global.impl.ConfigMapper;
import stroom.legacy.db.migration.V07_00_00_1202__property_rename.Mapping;
import stroom.legacy.model_6_1.DefaultProperties;
import stroom.legacy.model_6_1.GlobalProperty;
import stroom.util.config.PropertyUtil.Prop;
import stroom.util.shared.PropertyPath;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestV07_00_00_1202__property_rename {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestV07_00_00_1202__property_rename.class);

    /**
     * Ensures that all destination keys in the the property key migration script are valid with the
     * java config object model. It should ensure we are migrating to valid keys.
     * <p>
     * **IMPORTANT**
     * This test is only useful until we have formally released v7. Once that is released this test will
     * need to be removed, or used to test future property migrations, otherwise it will fail when
     * <p>
     * If it fails it is probably because the config model has changed so you need to look in
     * {@link V07_00_00_1202__property_rename} and change to TO mapping, or remove a mapping.
     */
//    @Test
//    void testPropertyNames() {
//        ConfigMapper configMapper = new ConfigMapper(new AppConfig());
//
//        final SoftAssertions softAssertions = new SoftAssertions();
//        V07_00_00_1202__property_rename.MAPPINGS.forEach((sourceKey, destKey) -> {
//            boolean result = configMapper.validatePropertyPath(PropertyPath.fromPathString(destKey));
//
//            softAssertions.assertThat(result)
//                    .describedAs("Destination key %s does not exist in the model", destKey)
//                    .isTrue();
//        });
//
//        softAssertions.assertAll();
//    }
    private static final Set<String> IGNORE = new HashSet<>();

    static {
        IGNORE.add("stroom.advertisedUrl");
        IGNORE.add("stroom.auth.authentication.service.url");
        IGNORE.add("stroom.auth.jwt.enabletokenrevocationcheck");
        IGNORE.add("stroom.auth.clientId");
        IGNORE.add("stroom.auth.jwt.issuer");
        IGNORE.add("stroom.auth.clientSecret");
        IGNORE.add("stroom.auth.services.url");
        IGNORE.add("stroom.auth.services.verifyingSsl");

        IGNORE.add("stroom.kafka.bootstrap.servers");

        IGNORE.add("stroom.proxy.store.dir");
        IGNORE.add("stroom.proxy.store.format");
        IGNORE.add("stroom.proxy.store.rollCron");

        IGNORE.add("stroom.proxyBufferSize");
        IGNORE.add("stroom.streamstore.resilientReplicationCount");
        IGNORE.add("stroom.streamstore.preferLocalVolumes");
        IGNORE.add("stroom.streamTask.deleteBatchSize");
        IGNORE.add("stroom.benchmark.streamCount");
        IGNORE.add("stroom.benchmark.recordCount");
        IGNORE.add("stroom.benchmark.concurrentWriters");
        IGNORE.add("stroom.rack");
        IGNORE.add("stroom.pipeline.parser.maxPoolSize");
        IGNORE.add("stroom.pipeline.schema.maxPoolSize");
        IGNORE.add("stroom.pipeline.xslt.maxPoolSize");
        IGNORE.add("stroom.jpaHbm2DdlAuto");
        IGNORE.add("stroom.jpaDialect");
        IGNORE.add("stroom.showSql");
        
        IGNORE.add("stroom.db.connectionPool.initialPoolSize");
        IGNORE.add("stroom.db.connectionPool.minPoolSize");
        IGNORE.add("stroom.db.connectionPool.maxPoolSize");
        IGNORE.add("stroom.db.connectionPool.idleConnectionTestPeriod");
        IGNORE.add("stroom.db.connectionPool.maxIdleTime");
        IGNORE.add("stroom.db.connectionPool.acquireIncrement");
        IGNORE.add("stroom.db.connectionPool.acquireRetryAttempts");
        IGNORE.add("stroom.db.connectionPool.acquireRetryDelay");
        IGNORE.add("stroom.db.connectionPool.checkoutTimeout");
        IGNORE.add("stroom.db.connectionPool.maxIdleTimeExcessConnections");
        IGNORE.add("stroom.db.connectionPool.maxConnectionAge");
        IGNORE.add("stroom.db.connectionPool.unreturnedConnectionTimeout");
        IGNORE.add("stroom.db.connectionPool.numHelperThreads");

        IGNORE.add("stroom.security.userNamePattern");

        IGNORE.add("stroom.serviceDiscovery.simpleLookup.basePath");

        IGNORE.add("stroom.services.authentication.docRefType");
        IGNORE.add("stroom.services.authentication.name");
        IGNORE.add("stroom.services.authentication.version");
        IGNORE.add("stroom.services.authorisation.docRefType");
        IGNORE.add("stroom.services.authorisation.name");
        IGNORE.add("stroom.services.authorisation.version");
        IGNORE.add("stroom.services.sqlStatistics.docRefType");
        IGNORE.add("stroom.services.sqlStatistics.name");
        IGNORE.add("stroom.services.sqlStatistics.version");
        IGNORE.add("stroom.services.stroomIndex.docRefType");
        IGNORE.add("stroom.services.stroomIndex.name");
        IGNORE.add("stroom.services.stroomIndex.version");
        IGNORE.add("stroom.services.stroomStats.docRefType");
        IGNORE.add("stroom.services.stroomStats.name");
        IGNORE.add("stroom.services.stroomStats.version");

        IGNORE.add("stroom.statistics.legacy.statisticAggregationBatchSize");

        IGNORE.add("stroom.statistics.sql.db.connectionPool.initialPoolSize");
        IGNORE.add("stroom.statistics.sql.db.connectionPool.minPoolSize");
        IGNORE.add("stroom.statistics.sql.db.connectionPool.maxPoolSize");
        IGNORE.add("stroom.statistics.sql.db.connectionPool.idleConnectionTestPeriod");
        IGNORE.add("stroom.statistics.sql.db.connectionPool.maxIdleTime");
        IGNORE.add("stroom.statistics.sql.db.connectionPool.acquireIncrement");
        IGNORE.add("stroom.statistics.sql.db.connectionPool.acquireRetryAttempts");
        IGNORE.add("stroom.statistics.sql.db.connectionPool.acquireRetryDelay");
        IGNORE.add("stroom.statistics.sql.db.connectionPool.checkoutTimeout");
        IGNORE.add("stroom.statistics.sql.db.connectionPool.maxIdleTimeExcessConnections");
        IGNORE.add("stroom.statistics.sql.db.connectionPool.maxConnectionAge");
        IGNORE.add("stroom.statistics.sql.db.connectionPool.unreturnedConnectionTimeout");
        IGNORE.add("stroom.statistics.sql.db.connectionPool.numHelperThreads");

        IGNORE.add("stroom.entity.maxCacheSize");
        IGNORE.add("stroom.referenceData.mapStore.maxCacheSize");
        IGNORE.add("stroom.loginHTML");

        IGNORE.add("stroom.security.documentPermissions.maxCacheSize");

        IGNORE.add("stroom.statistics.common.statisticEngines");
        IGNORE.add("stroom.statistics.sql.search.resultHandlerBatchSize");

        IGNORE.add("stroom.uiUrl");
        IGNORE.add("stroom.volumes.createDefaultOnStart");
    }

    @TestFactory
    Stream<DynamicTest> testDefaultProperties() {
        final Map<String, Mapping> mappings = V07_00_00_1202__property_rename
                .MAPPINGS
                .stream()
                .collect(Collectors.toMap(Mapping::getOldName, Function.identity()));
        final ConfigMapper configMapper = new ConfigMapper(new AppConfig());

        final List<GlobalProperty> defaultProperties = DefaultProperties.getList();
        return defaultProperties
                .stream()
                .sorted(Comparator.comparing(GlobalProperty::getName))
                .map(defaultProperty -> {
                    final String oldName = defaultProperty.getName();
                    if (IGNORE.contains(oldName)) {
                        return null;
                    }

                    return DynamicTest.dynamicTest(oldName, () -> {
                        final Mapping mapping = mappings.get(oldName);
                        assertThat(mapping)
                                .describedAs("No mapping exists for %s", oldName)
                                .isNotNull();

                        final String newName = mapping.getNewName();
                        final PropertyPath propertyPath = PropertyPath.fromPathString(newName);
                        final Optional<Prop> optionalProp = configMapper.getProp(propertyPath);
                        assertThat(optionalProp.isPresent())
                                .describedAs("Destination key %s does not exist in the model", newName)
                                .isTrue();

                        final String oldValue = defaultProperty.getValue();
                        final String mappedValue = mapping.getSerialisationMappingFunc().apply(oldValue);
                        final Object value = configMapper.convertValue(propertyPath, mappedValue);

                        final Prop prop = optionalProp.get();
                        prop.getSetter().invoke(prop.getParentObject(), value);



                        final String oldValue2 = defaultProperty.getDefaultValue();
                        final String mappedValue2 = mapping.getSerialisationMappingFunc().apply(oldValue2);
                        final Object value2 = configMapper.convertValue(propertyPath, mappedValue2);

                        prop.getSetter().invoke(prop.getParentObject(), value2);
                    });
                })
                .filter(Objects::nonNull);
    }
//
//            boolean result = V07_00_00_1202__property_rename.FROM_TO_MAP.containsKey(name);
//            softAssertions.assertThat(result)
//                    .describedAs("Destination key %s does not exist in the model", name)
//                    .isTrue();
//        });
//        return IntStream.rangeClosed(1, 8)
//                .boxed()
//                .map(len ->
//                        DynamicTest.dynamicTest("length " + len, () -> {
//                            length.set(len);
//                            long val = UnsignedBytesInstances.of(len).getMaxVal();
//                            final UnsignedLong unsignedLong = UnsignedLong.of(val, len);
//
//                            doSerialisationDeserialisationTest(unsignedLong);
//                        }));

//
//
//    @Test
//    void testDefaultProperties() {
//        final List<GlobalProperty> defaultProperties = DefaultProperties.getList();
//        final SoftAssertions softAssertions = new SoftAssertions();
//        defaultProperties.forEach(defaultProperty -> {
//            final String name = defaultProperty.getName();
//            boolean result = V07_00_00_1202__property_rename.FROM_TO_MAP.containsKey(name);
//            softAssertions.assertThat(result)
//                    .describedAs("Destination key %s does not exist in the model", name)
//                    .isTrue();
//        });
//        softAssertions.assertAll();
//    }

    @Test
    void testModuleStringDurationToDurationConversion1() {
        doConversionTest(
                V07_00_00_1202__property_rename::modelStringDurationToStroomDuration,
                "30d",
                "P30D");
        doConversionTest(
                V07_00_00_1202__property_rename::modelStringDurationToStroomDuration,
                "1H",
                "PT1H");
    }

    @Test
    void testListConversion() {
        doConversionTest(
                V07_00_00_1202__property_rename::commaDelimitedStringToListOfString,
                "one,two,three",
                ",one,two,three");
        doConversionTest(
                V07_00_00_1202__property_rename::commaDelimitedStringToListOfString,
                "one",
                ",one");
        doConversionTest(
                V07_00_00_1202__property_rename::commaDelimitedStringToListOfString,
                "",
                null);
        doConversionTest(
                V07_00_00_1202__property_rename::commaDelimitedStringToListOfString,
                null,
                null);
    }

    @Test
    void testDocRefConversion() {
        doConversionTest(
                V07_00_00_1202__property_rename::delimitedDocRefsToListOfDocRefs,
                "docRef(type1,uuid1,name1),docRef(type2,uuid2,name2)",
                "|,docRef(type1,uuid1,name1)|,docRef(type2,uuid2,name2)");
        doConversionTest(
                V07_00_00_1202__property_rename::delimitedDocRefsToListOfDocRefs,
                "docRef(type1,uuid1,name1)",
                "|,docRef(type1,uuid1,name1)");
        doConversionTest(
                V07_00_00_1202__property_rename::delimitedDocRefsToListOfDocRefs,
                "",
                null);
        doConversionTest(
                V07_00_00_1202__property_rename::delimitedDocRefsToListOfDocRefs,
                null,
                null);
    }

    @Test
    void test() {
        LOGGER.info(Duration.parse("P30D").toString());
    }

    void doConversionTest(final Function<String, String> func, final String oldValue, final String expectedValue) {
        String newValue = func.apply(oldValue);

        LOGGER.info("[{}] => [{}]", oldValue, newValue);

        Assertions.assertThat(newValue).isEqualTo(expectedValue);
    }
}
