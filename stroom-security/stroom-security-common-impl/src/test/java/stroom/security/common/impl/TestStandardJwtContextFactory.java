package stroom.security.common.impl;

import stroom.security.common.impl.StandardJwtContextFactory.JwsParts;
import stroom.security.openid.api.OpenId;
import stroom.util.NullSafe;
import stroom.util.exception.ThrowingConsumer;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.junit.jupiter.api.Test;

import java.io.IOException;
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
                        StandardJwtContextFactory.SIGNER_HEADER_KEY,
                        "arn:aws:elasticloadbalancing:region-x:1234:loadbalancer/app/MyApp/5678",
                        OpenId.KEY_ID,
                        "999"));

        final String uri = StandardJwtContextFactory.getAwsPublicKeyUri(jwsParts);

        assertThat(uri)
                .isEqualTo("https://public-keys.auth.elb.region-x.amazonaws.com/999");
    }


    @Test
    void testJsonParse() throws IOException {
        final String json = """
                {
                    "alg": "algorithm",
                    "kid": "12345678-1234-1234-1234-123456789012",
                    "signer": "xxx", 
                    "iss": "url",
                    "client": "client-id",
                    "exp": "expiration"
                 } """;

        JsonFactory jfactory = new JsonFactory();
        JsonParser jParser = null;
        String value = null;
        try {
            jParser = jfactory.createParser(json);
            while (jParser.nextToken() != JsonToken.END_OBJECT) {
                String fieldname = jParser.getCurrentName();
                if ("signer".equals(fieldname)) {
                    // Advance to field
                    value = jParser.nextTextValue();
                    break;
                }
            }
        } finally {
            NullSafe.consume(jParser, ThrowingConsumer.unchecked(JsonParser::close));
        }

        assertThat(value)
                .isEqualTo("xxx");
    }

    @Test
    void name() {
        final String[] split = "aa.bb".split("\\.");
        assertThat(split)
                .hasSize(2);
    }
}
