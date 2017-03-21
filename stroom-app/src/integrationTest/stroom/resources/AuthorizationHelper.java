package stroom.resources;

import org.glassfish.jersey.internal.util.Base64;

public class AuthorizationHelper {
    public static String getHeaderWithValidBasicAuthCredentials() {
        String encoding = Base64.encodeAsString("admin:admin");
        return "Basic " + encoding;
    }

    public static String getHeaderWithInvalidBasicAuthCredentials() {
        String encoding = Base64.encodeAsString("invalidUsername:invalidPassword");
        return "Basic " + encoding;
    }
}
