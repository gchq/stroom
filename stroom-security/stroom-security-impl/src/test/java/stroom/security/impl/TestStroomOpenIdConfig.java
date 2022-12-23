package stroom.security.impl;

import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfig;
import stroom.test.common.AbstractValidatorTest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class TestStroomOpenIdConfig extends AbstractValidatorTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestStroomOpenIdConfig.class);

    @Test
    void testSerDeSer() throws JsonProcessingException {

        final ObjectMapper objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        for (final IdpType idpType : IdpType.values()) {
            LOGGER.info("idpType: {}", idpType);

            final StroomOpenIdConfig stroomOpenIdConfig = new StroomOpenIdConfig()
                    .withIdentityProviderType(idpType);

            Assertions.assertThat(stroomOpenIdConfig.getIdentityProviderType())
                    .isNotNull()
                    .isEqualTo(idpType);

            final String json = objectMapper.writeValueAsString(stroomOpenIdConfig);

            LOGGER.info("json\n{}", json);

            final OpenIdConfig openIdConfig2 = objectMapper.readValue(json, StroomOpenIdConfig.class);

            Assertions.assertThat(openIdConfig2)
                    .isEqualTo(stroomOpenIdConfig);

            // Use lower case enum values to make sure we can de-ser them
            final String json2 = json.replace(idpType.name().toUpperCase(),
                    idpType.name().toLowerCase());

            LOGGER.info("json2\n{}", json2);

            final OpenIdConfig openIdConfig3 = objectMapper.readValue(json2, StroomOpenIdConfig.class);

            Assertions.assertThat(openIdConfig3)
                    .isEqualTo(stroomOpenIdConfig);
        }
    }

}
