package stroom.config.global.impl.db.migration;

import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import stroom.config.app.AppConfig;
import stroom.config.global.impl.ConfigMapper;

class TestV07_00_00_02__property_rename {

    /**
     * Ensures that all destination keys in the the property key migration script are valid with the
     * java config object model. It should ensure we are migrating to valid keys.
     *
     * **IMPORTANT**
     * This test is only useful until we have formally released v7. Once that is released this test will
     * need to be removed, or used to test future property migrations, otherwise it will fail when
     *
     * If it fails it is probably because the config model has changed so you need to look in
     * {@link V07_00_00_02__property_rename} and change to TO mapping, or remove a mapping.
     */
    @Test
    void testPropertyNames() {

        ConfigMapper configMapper = new ConfigMapper(new AppConfig());

        final SoftAssertions softAssertions = new SoftAssertions();
        V07_00_00_02__property_rename.FROM_TO_MAP.forEach((sourceKey, destKey) -> {
            boolean result = configMapper.validatePropertyPath(destKey);

            softAssertions.assertThat(result)
                    .describedAs("Destination key %s does not exist in the model", destKey)
                    .isTrue();
        });

        softAssertions.assertAll();
    }

}