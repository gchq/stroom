package stroom.security.common.impl;

import stroom.security.common.impl.StandardJwtContextFactory.JwsParts;
import stroom.security.openid.api.OpenId;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestStandardJwtContextFactory {

    @Test
    void getAwsPublicKeyUriFromSigner() {
        JwsParts jwsParts = new JwsParts(
                null,
                null,
                null,
                null,
                Map.of(
                        StandardJwtContextFactory.AMZN_OIDC_SIGNER_HEADER_KEY,
                        "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678",
                        OpenId.KEY_ID,
                        "999"));

        final String uri = StandardJwtContextFactory.getAwsPublicKeyUri(jwsParts);

        assertThat(uri)
                .isEqualTo("https://public-keys.auth.elb.region-x.amazonaws.com/999");
    }
}
