package stroom.security.impl;

import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfig;
import stroom.test.common.AbstractValidatorTest;
import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

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
        typesMaps.put(null, null);

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

            final OpenIdConfig openIdConfig2 = objectMapper.readValue(json, StroomOpenIdConfig.class);

            Assertions.assertThat(openIdConfig2)
                    .isEqualTo(stroomOpenIdConfig);

            // Use lower case enum values to make sure we can de-ser them
            final String json2 = inputType != null
                    ? json.replace(
                    inputType.name().toUpperCase(),
                    inputType.name().toLowerCase())
                    : json;

            LOGGER.info("json2\n{}", json2);

            final OpenIdConfig openIdConfig3 = objectMapper.readValue(json2, StroomOpenIdConfig.class);

            Assertions.assertThat(openIdConfig3)
                    .isEqualTo(stroomOpenIdConfig);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
