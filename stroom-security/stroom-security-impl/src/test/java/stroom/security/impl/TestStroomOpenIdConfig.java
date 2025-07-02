package stroom.security.impl;

import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.test.common.AbstractValidatorTest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

class TestStroomOpenIdConfig extends AbstractValidatorTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestStroomOpenIdConfig.class);

    @TestFactory
    Stream<DynamicTest> testSerDeSer() throws JsonProcessingException {

        final ObjectMapper objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        final Map<IdpType, IdpType> typesMaps = new HashMap<>();
        for (final IdpType idpType : IdpType.values()) {
            typesMaps.put(idpType, idpType);
        }
        typesMaps.put(null, IdpType.INTERNAL_IDP);

        return typesMaps.entrySet()
                .stream()
                .map(entry -> {

                    final IdpType input = entry.getKey();
                    final IdpType expectedOutput = entry.getValue();
                    return DynamicTest.dynamicTest(
                            NullSafe.toStringOrElse(input, "null"),
                            () -> {
                                doTest(objectMapper, entry.getKey(), expectedOutput);
                            });
                });
    }

    private void doTest(final ObjectMapper objectMapper,
                        final IdpType inputType,
                        final IdpType expectedType) {
        try {
            LOGGER.info("idpType: {}", inputType);

            final StroomOpenIdConfig stroomOpenIdConfig = new StroomOpenIdConfig()
                    .withIdentityProviderType(inputType);

            Assertions.assertThat(stroomOpenIdConfig.getIdentityProviderType())
                    .isEqualTo(expectedType);

            final String json = objectMapper.writeValueAsString(stroomOpenIdConfig);

            LOGGER.info("json\n{}", json);

            final AbstractOpenIdConfig abstractOpenIdConfig2 = objectMapper.readValue(json, StroomOpenIdConfig.class);

            Assertions.assertThat(abstractOpenIdConfig2)
                    .isEqualTo(stroomOpenIdConfig);

            // Use lower case enum values to make sure we can de-ser them
            final String json2 = inputType != null
                    ? json.replace(
                    inputType.name().toUpperCase(),
                    inputType.name().toLowerCase())
                    : json;

            LOGGER.info("json2\n{}", json2);

            final AbstractOpenIdConfig abstractOpenIdConfig3 = objectMapper.readValue(json2, StroomOpenIdConfig.class);

            Assertions.assertThat(abstractOpenIdConfig3)
                    .isEqualTo(stroomOpenIdConfig);
        } catch (final JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
