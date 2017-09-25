package stroom.security.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.dropwizard.Configuration;
import io.dropwizard.testing.junit.DropwizardAppRule;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientResponse;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import stroom.startup.App;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;

public class TestAuthenticationResource {


    private static App app;

    @ClassRule
    public static final DropwizardAppRule<Configuration> RULE = new DropwizardAppRule<>(App.class, "dev.yml");

    @BeforeClass
    public static void setupClass() {
        app = RULE.getApplication();
    }

    @Test
    public void testValidCredentials() throws JsonProcessingException, InterruptedException {
//        app.waitForApplicationStart();
        // Given
        Client client = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));

        // When
        Response response = client
                .target(AuthorizationHelper.GET_TOKEN_TARGET)
                .request()
                .header("Authorization", AuthorizationHelper.getHeaderWithValidBasicAuthCredentials())
                .get();

        // Then
        Assert.assertThat(response.getStatus(), CoreMatchers.equalTo(Response.Status.OK.getStatusCode()));
        String jwtToken = response.readEntity(String.class);
        Assert.assertThat(jwtToken, CoreMatchers.notNullValue());
    }

    @Test
    public void testInvalidCredentials() throws JsonProcessingException, InterruptedException {

//        app.waitForApplicationStart();
        // Given
        Client client = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));

        // When
        Response response = client
                .target(AuthorizationHelper.GET_TOKEN_TARGET)
                .request()
                .header("Authorization", AuthorizationHelper.getHeaderWithInvalidBasicAuthCredentials())
                .get();

        // Then
        Assert.assertThat(response.getStatus(), CoreMatchers.equalTo(Response.Status.UNAUTHORIZED.getStatusCode()));
    }
}
