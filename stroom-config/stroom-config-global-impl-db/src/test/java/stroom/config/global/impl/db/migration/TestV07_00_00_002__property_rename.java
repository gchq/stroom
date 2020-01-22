package stroom.config.global.impl.db.migration;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.config.app.AppConfig;
import stroom.config.global.impl.ConfigMapper;
import stroom.util.shared.PropertyPath;

import java.time.Duration;
import java.util.function.Function;

class TestV07_00_00_002__property_rename {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestV07_00_00_002__property_rename.class);

    /**
     * Ensures that all destination keys in the the property key migration script are valid with the
     * java config object model. It should ensure we are migrating to valid keys.
     *
     * **IMPORTANT**
     * This test is only useful until we have formally released v7. Once that is released this test will
     * need to be removed, or used to test future property migrations, otherwise it will fail when
     *
     * If it fails it is probably because the config model has changed so you need to look in
     * {@link V07_00_00_002__property_rename} and change to TO mapping, or remove a mapping.
     */
    @Test
    void testPropertyNames() {

        ConfigMapper configMapper = new ConfigMapper(new AppConfig());

        final SoftAssertions softAssertions = new SoftAssertions();
        V07_00_00_002__property_rename.FROM_TO_MAP.forEach((sourceKey, destKey) -> {
            boolean result = configMapper.validatePropertyPath(PropertyPath.fromPathString(destKey));

            softAssertions.assertThat(result)
                    .describedAs("Destination key %s does not exist in the model", destKey)
                    .isTrue();
        });

        softAssertions.assertAll();
    }

    @Test
    void testModuleStringDurationToDurationConversion1() {
        doConversionTest(V07_00_00_002__property_rename::modelStringDurationToStroomDuration,"30d", "P30D");
        doConversionTest(V07_00_00_002__property_rename::modelStringDurationToStroomDuration,"1H", "PT1H");
    }

    @Test
    void test() {
        LOGGER.info(Duration.parse("P30D").toString());
    }

    void doConversionTest(final Function<String, String> func, final String oldValue, final String expectedValue) {
        String newValue = func.apply(oldValue);

        LOGGER.info("{} => {}", oldValue, newValue);

        Assertions.assertThat(newValue).isEqualTo(expectedValue);
    }

}