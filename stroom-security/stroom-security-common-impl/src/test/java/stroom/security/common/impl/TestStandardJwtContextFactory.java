package stroom.security.common.impl;

import stroom.security.common.impl.StandardJwtContextFactory.JwsParts;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

class TestStandardJwtContextFactory {

    @Test
    void getAwsPublicKeyUriFromSigner() {
        final String json = """
                {
                  "signer": "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678",
                  "kid": "999"
                }""";

        final String header = Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));

        JwsParts jwsParts = new JwsParts(
                null,
                header,
                null,
                null);

        final String uri = StandardJwtContextFactory.getAwsPublicKeyUri(jwsParts);

        assertThat(uri)
                .isEqualTo("https://public-keys.auth.elb.region-x.amazonaws.com/999");
    }
}
