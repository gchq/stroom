package stroom.resources.authorisation.v1;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.internal.util.Base64;
import stroom.resources.RegisteredService;
import stroom.resources.ResourcePaths;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

public class AuthorizationHelper {

    public static final String GET_TOKEN_TARGET = "http://localhost:8080" +
            ResourcePaths.ROOT_PATH +
            RegisteredService.AUTHENTICATION_V1.getVersionedPath() +
            "/getToken";

    public static String getHeaderWithValidBasicAuthCredentials() {
        String encoding = Base64.encodeAsString("admin:admin");
        return "Basic " + encoding;
    }

    public static String getHeaderWithInvalidBasicAuthCredentials() {
        String encoding = Base64.encodeAsString("invalidUsername:invalidPassword");
        return "Basic " + encoding;
    }

    public static String fetchJwtToken(){
        Client client = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));
        Response response = client
                .target(GET_TOKEN_TARGET)
                .request()
                .header("Authorization", AuthorizationHelper.getHeaderWithValidBasicAuthCredentials())
                .get();
        // Then
        String jwtToken = response.readEntity(String.class);
        return jwtToken;
    }
}
