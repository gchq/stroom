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

public class TestAuthenticationResource {
    @Test
    public void testValidCredentials() throws JsonProcessingException {
        // Given
        Client client = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));

        // When
        Response response = client
                .target("http://localhost:8080/api/authentication/getToken")
                .request()
                .header("Authorization", AuthorizationHelper.getHeaderWithValidBasicAuthCredentials())
                .get();

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
        Response response = client
                .target("http://localhost:8080/api/authentication/getToken")
                .request()
                .header("Authorization", AuthorizationHelper.getHeaderWithInvalidBasicAuthCredentials())
                .get();

        // Then
        Assert.assertThat(response.getStatus(), CoreMatchers.equalTo(Response.Status.UNAUTHORIZED.getStatusCode()));
    }
}
