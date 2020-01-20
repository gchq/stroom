/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.jose4j.jwk.PublicJsonWebKey;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.jwt.consumer.InvalidJwtException;
import org.jose4j.jwt.consumer.JwtConsumer;
import org.jose4j.jwt.consumer.JwtConsumerBuilder;
import org.jose4j.lang.JoseException;
import stroom.auth.resources.authentication.v1.LoginResponse;
import stroom.auth.service.ApiClient;
import stroom.auth.service.ApiException;
import stroom.auth.service.App;
import stroom.auth.service.api.ApiKeyApi;
import stroom.auth.service.api.AuthenticationApi;
import stroom.auth.service.api.model.Credentials;
import stroom.auth.service.api.model.IdTokenRequest;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

public class AuthenticationFlowHelper {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(AuthenticationFlowHelper.class);

    private static final String CLIENT_ID = "PZnJr8kHRKqnlJRQThSI";
    private static final String CLIENT_SECRET = "OtzHiAWLj8QWcwO2IxXmqxpzE2pyg0pMKCghR2aU";

    public static String authenticateAsAdmin() throws JoseException, ApiException, URISyntaxException, IOException, UnirestException {
        return authenticateAs("admin", "admin");
    }

    public static String authenticateAs(String userEmail, String password) throws JoseException, ApiException, URISyntaxException, IOException, UnirestException {
        // We have to change the password for admin because otherwise the flow might demand a changepassword --
        // it certainly would in TravisCI.
        HttpResponse changePasswordResponse = Unirest.post("http://localhost:8099/authentication/v1/changePassword")
                .header("Content-Type", "application/json")
                .body("{\"email\":\"admin\", \"oldPassword\":\"admin\", \"newPassword\":\"admin\"}")
                .asString();

        if(changePasswordResponse.getStatus() != Response.Status.OK.getStatusCode()){
            fail("Unable to change password! " + changePasswordResponse.getStatusText());
        }

        // We need to use a real-ish sort of nonce otherwise the OpenId tokens might end up being identical.
        String nonce = UUID.randomUUID().toString();
        String sessionId = sendInitialAuthenticationRequest(nonce);
        String accessCode = null;
        try {
            accessCode = performLogin(sessionId, userEmail, password);
        } catch (ApiException e) {
            fail("Could not log the user in as '"+userEmail+"/"+password+"'!");
        }
        String idToken = exchangeAccessCodeForIdToken(sessionId, accessCode);
        PublicJsonWebKey jwk = fetchPublicJwk();
        checkIdTokenContainsNonce(idToken, nonce, jwk);
        return idToken;
    }

    /**
     * The standard authentication request, for when the client doesn't care about checking their nonce.
     */
    public static String sendInitialAuthenticationRequest() throws MalformedURLException {
        return sendInitialAuthenticationRequest(UUID.randomUUID().toString());
    }

    /**
     * This is a standard authentication request, where the user has not logged in before.
     * <p>
     * This flow would redirect the user to login, but we're faking that too so we ignore the redirection.
     */
    public static String sendInitialAuthenticationRequest(String nonce) throws MalformedURLException {
        LOGGER.info("Sending initial authentication request.");
        // The authentication flow includes a redirect to login. We don't want
        // anything interactive in testing so we need to take some steps to prevent the redirect:
        // 1. Don't use Swagger client - Swagger client will always accept and act on redirects
        // 2. Tweak Unirest configuration to disable its redirect handling.
        StringBuilder authenticationRequestParams = new StringBuilder();
        authenticationRequestParams.append("?scope=openid");
        authenticationRequestParams.append("&response_type=code");
        authenticationRequestParams.append("&client_id=");
        authenticationRequestParams.append(CLIENT_ID);
        authenticationRequestParams.append("&redirect_url=http://fakedomain.com");
        authenticationRequestParams.append("&state=");
        authenticationRequestParams.append("&nonce=");
        authenticationRequestParams.append(nonce);
        String authenticationRequestUrl =
                "http://localhost:8099/authentication/v1/authenticate" + authenticationRequestParams;
        // Disable redirect handling -- see note above.
        Unirest.setHttpClient(org.apache.http.impl.client.HttpClients.custom()
                .disableRedirectHandling()
                .build());


        HttpResponse authenticationRequestResponse = null;
        try {
            authenticationRequestResponse = Unirest
                    .get(authenticationRequestUrl)
                    .header("Content-Type", "application/json")
                    .asString();
        } catch (UnirestException e) {
            fail("Initial authentication request failed!");
        }

        assertThat(authenticationRequestResponse.getStatus()).isEqualTo(303);// 303 = See Other
        StringBuilder redirectionPathBuilder = new StringBuilder();
        redirectionPathBuilder.append("/s/login?error=login_required&state=&clientId=");
        redirectionPathBuilder.append(CLIENT_ID);
        redirectionPathBuilder.append("&redirectUrl=http%3A%2F%2Ffakedomain.com");
        String redirectionPath = redirectionPathBuilder.toString();
        URL location = new URL(authenticationRequestResponse.getHeaders().get("Location").get(0));
        String locationPath = location.getPath() + "?" + location.getQuery();
        assertThat(locationPath).isEqualTo(redirectionPath);

        String sessionCookie = authenticationRequestResponse.getHeaders().get("Set-Cookie").get(0);
        // We need to do a bit of splitting and getting to pull out the sessionId. The cookie looks like this:
        // sessionId=d8948ce1-45a3-4422-aad3-fb4aa2228bdb;Version=1;Comment="Stroom session cookie";...
        String sessionId = sessionCookie.split(";")[0].split("=")[1];
        assertThat(sessionId).isNotEmpty();

        return sessionId;
    }

    /**
     * This logs the user in, using a given sessionId. In return it gets an access code.
     * <p>
     * The sessionId would be stored in a cookie and a normal relying party would not have to do this.
     */
    public static String performLogin(String sessionId, String username, String password) throws ApiException, URISyntaxException, IOException {
        // We need to use UniRest again because we're not a browser and we need to manually add in the cookies.
        Credentials credentials = new Credentials();
        credentials.setEmail(username);
        credentials.setPassword(password);
        credentials.setRequestingClientId(CLIENT_ID);
        String cookies = App.SESSION_COOKIE_NAME + "=" + sessionId;
        HttpResponse loginResponse = null;
        try {
            loginResponse = Unirest
                    .post("http://localhost:8099/authentication/v1/authenticate")
                    .header("Content-Type", "application/json")
                    .header("Cookie", cookies)
                    .body("{\"email\":\""+username+"\", \"password\":\""+password+"\", \"requestingClientId\":\""+CLIENT_ID+"\"}")
                    .asString();
        } catch (UnirestException e) {
            fail("Initial authentication request failed!");
        }

        if(loginResponse.getStatus()!=200){
            // The Swagger ApiException is useful for returning information about non-200 responses to tests.
            throw new ApiException((String)loginResponse.getBody(), loginResponse.getStatus(), null, null);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        LoginResponse loginResponseObject = objectMapper.readValue(loginResponse.getBody().toString(), LoginResponse.class);
        if(!loginResponseObject.isLoginSuccessful()) {
            throw new RuntimeException("LOGIN_FAILED");
        }
        URL postAuthenticationRedirectUrl = new URL(loginResponseObject.getRedirectUrl());

        // The normally supplied advertised host doesn't work on Travis, so we need to hack the URL so it uses localhost.
        String modifiedPostAuthenticationRedirectUrl = String.format(
                "http://localhost:8099%s?%s",
                // 'authenticationService' refers to the public path in nginx -- the advertised host. But tests
                // have no nginx so we need to refer to the configured path, which is 'authentication'.
                postAuthenticationRedirectUrl.getPath().replaceAll("api/auth/authentication", "authentication"),
                postAuthenticationRedirectUrl.getQuery());

        HttpResponse postAuthenticationRedirectResponse = null;
        LOGGER.info("postAuthenticationRedirectUri is {}", modifiedPostAuthenticationRedirectUrl);
        try {
            postAuthenticationRedirectResponse = Unirest
                    .get(modifiedPostAuthenticationRedirectUrl)
                    .header("Content-Type", "application/json")
                    .header("Cookie", cookies)
                    .asString();
        } catch (UnirestException e) {
            fail("Unable to follow postAuthenticationRedirect! " + e.toString());
        }
        String redirectUri = postAuthenticationRedirectResponse.getHeaders().get("Location").get(0);
        LOGGER.info("redirectUrl:{}", redirectUri);

        List<NameValuePair> params = URLEncodedUtils.parse(new URI(redirectUri), StandardCharsets.UTF_8);
        String accessCode = params.stream()
                .filter(pair -> pair.getName().equals("accessCode"))
                .findFirst().orElseThrow(() -> new RuntimeException("No access code is present!"))
                .getValue();

        assertThat(accessCode).isNotEmpty();
        return accessCode;
    }

    public static String exchangeAccessCodeForIdToken(String sessionId, String accessCode) {
        LOGGER.info("Exchanging the access code for an ID token.");
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath("http://localhost:8099");
        AuthenticationApi authenticationApi = new AuthenticationApi(apiClient);
        String idToken = null;
        IdTokenRequest idTokenRequest = new IdTokenRequest()
                .clientId(CLIENT_ID)
                .clientSecret(CLIENT_SECRET)
                .accessCode(accessCode);
        try {
            idToken = authenticationApi.getIdToken(idTokenRequest);
        } catch (ApiException e) {
            fail("Request to exchange access code for id token failed!", e);
        }

        assertThat(idToken).isNotEmpty();

        return idToken;
    }

    public static PublicJsonWebKey fetchPublicJwk() throws ApiException, JoseException {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath("http://localhost:8099");
        ApiKeyApi apiKeyApi = new ApiKeyApi(apiClient);
        String jwkAsJson = apiKeyApi.getPublicKey();
        return RsaJsonWebKey.Factory.newPublicJwk(jwkAsJson);
    }

    public static void checkIdTokenContainsNonce(String idToken, String nonce, PublicJsonWebKey jwk) throws JoseException {
        LOGGER.info("Verifying the nonce is in the id token");
        JwtConsumer consumer = new JwtConsumerBuilder()
                .setAllowedClockSkewInSeconds(30) // allow some leeway in validating time based claims to account for clock skew
                .setRequireSubject() // the JWT must have a subject claim
                .setVerificationKey(jwk.getPublicKey()) // verify the signature with the public key
                .setRelaxVerificationKeyValidation() // relaxes key length requirement
                .setExpectedIssuer("stroom")
                .setExpectedAudience(CLIENT_ID)
                .build();
        JwtClaims claims = null;
        try {
            claims = consumer.processToClaims(idToken);
        } catch (InvalidJwtException e) {
            fail("Bad JWT returned from auth service", e);
        }
        String nonceHash = (String) claims.getClaimsMap().get("nonce");
        assertThat(nonceHash).isEqualTo(nonce);
    }
}
