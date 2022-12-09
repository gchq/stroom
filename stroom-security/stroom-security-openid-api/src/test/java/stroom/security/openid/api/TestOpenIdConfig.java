package stroom.security.openid.api;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class TestOpenIdConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            TestOpenIdConfig.class);

    @Test
    void testSerDeSer() throws JsonProcessingException {

        final ObjectMapper objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        for (final IdpType idpType : IdpType.values()) {
            LOGGER.info("idpType: {}", idpType);
            final OpenIdConfig openIdConfig = new OpenIdConfig()
                    .withIdentityProviderType(idpType);

            Assertions.assertThat(openIdConfig.getIdentityProviderType())
                    .isNotNull()
                    .isEqualTo(idpType);

            final String json = objectMapper.writeValueAsString(openIdConfig);

            LOGGER.info("json\n{}", json);

            final OpenIdConfig openIdConfig2 = objectMapper.readValue(json, OpenIdConfig.class);

            Assertions.assertThat(openIdConfig2)
                    .isEqualTo(openIdConfig);

            // Use lower case enum values to make sure we can de-ser them
            final String json2 = json.replace(idpType.name().toUpperCase(),
                    idpType.name().toLowerCase());

            LOGGER.info("json2\n{}", json2);

            final OpenIdConfig openIdConfig3 = objectMapper.readValue(json2, OpenIdConfig.class);

            Assertions.assertThat(openIdConfig3)
                    .isEqualTo(openIdConfig);
        }
    }
}
