package stroom.proxy.app;

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

import java.util.Set;
import javax.validation.ConstraintViolation;

class TestProxyOpenIdConfig extends AbstractValidatorTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestProxyOpenIdConfig.class);

    @Test
    void testSerDeSer() throws JsonProcessingException {

        final ObjectMapper objectMapper = new ObjectMapper()
                .enable(SerializationFeature.INDENT_OUTPUT);

        for (final IdpType idpType : IdpType.values()) {
            LOGGER.info("idpType: {}", idpType);

            final ProxyOpenIdConfig proxyOpenIdConfig = new ProxyOpenIdConfig()
                    .withIdentityProviderType(idpType);

            Assertions.assertThat(proxyOpenIdConfig.getIdentityProviderType())
                    .isNotNull()
                    .isEqualTo(idpType);

            final String json = objectMapper.writeValueAsString(proxyOpenIdConfig);

            LOGGER.info("json\n{}", json);

            final OpenIdConfig openIdConfig2 = objectMapper.readValue(json, ProxyOpenIdConfig.class);

            Assertions.assertThat(openIdConfig2)
                    .isEqualTo(proxyOpenIdConfig);

            // Use lower case enum values to make sure we can de-ser them
            final String json2 = json.replace(idpType.name().toUpperCase(),
                    idpType.name().toLowerCase());

            LOGGER.info("json2\n{}", json2);

            final OpenIdConfig openIdConfig3 = objectMapper.readValue(json2, ProxyOpenIdConfig.class);

            Assertions.assertThat(openIdConfig3)
                    .isEqualTo(proxyOpenIdConfig);
        }
    }

    @Test
    void testValidation_valid() {

        final ProxyOpenIdConfig proxyOpenIdConfig = new ProxyOpenIdConfig();

        final Set<ConstraintViolation<ProxyOpenIdConfig>> constraintViolations = validateValidValue(
                proxyOpenIdConfig);
    }

    @Test
    void testValidation_invalidIdpType() {

        final ProxyOpenIdConfig stroomOpenIdConfig = new ProxyOpenIdConfig()
                .withIdentityProviderType(IdpType.INTERNAL);

        final Set<ConstraintViolation<ProxyOpenIdConfig>> constraintViolations = validateInvalidValue(
                stroomOpenIdConfig);

        Assertions.assertThat(constraintViolations)
                .hasSize(1)
                .first()
                .matches(violation -> violation.getMessage().contains("INTERNAL is not a valid value"));
    }
}
