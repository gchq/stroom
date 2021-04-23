package stroom.legacy.db.migration;

import stroom.config.app.AppConfig;
import stroom.config.global.impl.ConfigMapper;
import stroom.util.shared.PropertyPath;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.function.Function;

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
    @Test
    void testPropertyNames() {
        ConfigMapper configMapper = new ConfigMapper(new AppConfig());

        final SoftAssertions softAssertions = new SoftAssertions();
        V07_00_00_1202__property_rename.FROM_TO_MAP.forEach((sourceKey, destKey) -> {
            boolean result = configMapper.validatePropertyPath(PropertyPath.fromPathString(destKey));

            softAssertions.assertThat(result)
                    .describedAs("Destination key %s does not exist in the model", destKey)
                    .isTrue();
        });

        softAssertions.assertAll();
    }

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
                "");
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
                "");
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
