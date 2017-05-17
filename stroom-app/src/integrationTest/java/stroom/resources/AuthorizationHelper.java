package stroom.resources;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientResponse;
import org.glassfish.jersey.internal.util.Base64;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

public class AuthorizationHelper {
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
                .target("http://localhost:8080/api/authentication/getToken")
                .request()
                .header("Authorization", AuthorizationHelper.getHeaderWithValidBasicAuthCredentials())
                .get();
        // Then
        String jwtToken = response.readEntity(String.class);
        return jwtToken;
    }
}
