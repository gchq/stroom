package stroom.resources;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientResponse;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.util.function.Supplier;

public class TestAuthenticationResource {
    @Test
    public void testValidCredentials() throws JsonProcessingException {
        // Given
        Client client = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));

        // When
        Response response = httpGetWithJson(
                client,
                "http://localhost:8080/api/auth/getToken",
                AuthorizationHelper::getHeaderWithValidBasicAuthCredentials);

        // Then
        Assert.assertThat(response.getStatus(), CoreMatchers.equalTo(Response.Status.OK.getStatusCode()));
        String jwtToken = response.readEntity(String.class);
        Assert.assertThat(jwtToken, CoreMatchers.notNullValue());
    }

    @Test
    public void testInvalidCredentials() throws JsonProcessingException {
        // Given
        Client client = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));

        // When
        Response response = httpGetWithJson(
                client,
                "http://localhost:8080/api/auth/getToken",
                AuthorizationHelper::getHeaderWithInvalidBasicAuthCredentials);

        // Then
        Assert.assertThat(response.getStatus(), CoreMatchers.equalTo(Response.Status.UNAUTHORIZED.getStatusCode()));
    }

    private static Response httpGetWithJson(Client client, String url, Supplier<String> credentialFunc){
        Response response = client.target(url)
                .request()
                .header("Authorization", credentialFunc.get())
                .get();
        return response;
    }
}
