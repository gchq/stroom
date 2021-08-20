package stroom.security.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.HttpHeaders;

public class ClientSecurityUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ClientSecurityUtil.class);

    public static void addAuthorisationHeader(final Invocation.Builder builder, final String jws) {
        if (jws == null) {
            LOGGER.debug("The JWS is null");
        } else {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + jws);
        }
    }
}
