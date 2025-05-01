package stroom.security.identity.token;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.junit.jupiter.api.Test;

class TestJwkFactoryImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestJwkFactoryImpl.class);

    @Test
    void createPublicKey() {
        final JwkFactoryImpl jwkFactory = new JwkFactoryImpl();
        final String json = jwkFactory.asJson(jwkFactory.createPublicKey());
        LOGGER.info("json\n{}", json);
    }
}
