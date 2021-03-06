package stroom.legacy.db.migration;

import stroom.config.app.AppConfig;
import stroom.config.global.impl.ConfigMapper;
import stroom.legacy.db.migration.V07_00_00_1202__property_rename.Mapping;
import stroom.legacy.db.migration.V07_00_00_1202__property_rename.Mappings;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class TestV07_00_00_1202__property_rename {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestV07_00_00_1202__property_rename.class);

    @TestFactory
    Stream<DynamicTest> test55DefaultProperties() {
        return testDefaultProperties(stroom.legacy.model_5_5.DefaultProperties.getList());
    }

    @TestFactory
    Stream<DynamicTest> test61DefaultProperties() {
        return testDefaultProperties(stroom.legacy.model_6_1.DefaultProperties.getList());
    }

    Stream<DynamicTest> testDefaultProperties(final List<GlobalProperty> defaultProperties) {
        final Mappings mappings = new V07_00_00_1202__property_rename.Mappings();
        final ConfigMapper configMapper = new ConfigMapper(new AppConfig());

        return defaultProperties
                .stream()
                .sorted(Comparator.comparing(GlobalProperty::getName))
                .map(defaultProperty -> {
                    final String oldName = defaultProperty.getName();
                    if (mappings.ignore(oldName)) {
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
