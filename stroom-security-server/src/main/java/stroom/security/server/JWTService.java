package stroom.security.server;

import com.google.common.base.Strings;
import org.apache.shiro.web.util.WebUtils;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.client.ClientResponse;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.keys.HmacKey;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.core.Response;
import java.util.Optional;

import static org.jose4j.jws.AlgorithmIdentifiers.HMAC_SHA256;

// TODO: Do we still need this class? Could it be refactored?
@Component
public class JWTService {
    private static final Logger LOGGER = LoggerFactory.getLogger(JWTService.class);

    private static final String BEARER = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final String authenticationServiceUrl;

    public JWTService(
        @Value("#{propertyConfigurer.getProperty('stroom.security.authentication.url')}") final String authenticationServiceUrl){
        this.authenticationServiceUrl = authenticationServiceUrl;

        if (authenticationServiceUrl == null) {
            throw new SecurityException("No authentication service URL is defined");
        }
    }

    private static String toToken(byte[] key, JwtClaims claims) {
        final JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setAlgorithmHeaderValue(HMAC_SHA256);
        jws.setKey(new HmacKey(key));
        jws.setDoKeyValidation(false);

        try {
            return jws.getCompactSerialization();
        } catch (JoseException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public boolean containsValidJws(ServletRequest request) {
        Optional<String> authHeader = getAuthHeader(request);
        String jws;
        if (authHeader.isPresent()) {
            String bearerString = authHeader.get();

            if(bearerString.startsWith(BEARER)){
                // This chops out 'Bearer' so we get just the token.
                jws = bearerString.substring(BEARER.length());
            }
            else{
                jws = bearerString;
            }
            LOGGER.debug("Found auth header in request. It looks like this: {}", jws);
        }
        else {
            // If there's no token then we've nothing to do.
            return false;
        }

        try {
            JWTAuthenticationToken jwtAuthenticationToken = verifyToken(jws);
            return jwtAuthenticationToken.getUserId() != null;
        } catch (Exception e){
            LOGGER.error("Unable to verify token:", e.getMessage(), e);
            // If we get an exception verifying the token then we need to log the message
            // and continue as if the token wasn't provided.
            // TODO: decide if this should be handled by an exception and how
            return false;
        }

    }

    public Optional<String> getJws(ServletRequest request){
        Optional<String> authHeader = getAuthHeader(request);
        Optional<String> jws = Optional.empty();
        if (authHeader.isPresent()) {
            String bearerString = authHeader.get();
            if(bearerString.startsWith(BEARER)){
                // This chops out 'Bearer' so we get just the token.
                jws = Optional.of(bearerString.substring(BEARER.length()));
            }
            else{
                jws = Optional.of(bearerString);
            }
            LOGGER.debug("Found auth header in request. It looks like this: {}", jws);
        }
        return jws;
    }

    public Optional<JWTAuthenticationToken> verifyToken(ServletRequest request){
        //TODO We're not getting the token from the request any more.
        Optional<String> authHeader = getAuthHeader(request);
        Optional<String> authParam = getAuthParam(request);
        String jws;
        if (authHeader.isPresent()) {
            String bearerString = authHeader.get();

            if(bearerString.startsWith(BEARER)){
                // This chops out 'Bearer' so we get just the token.
                jws = bearerString.substring(BEARER.length());
            }
            else{
                jws = bearerString;
            }
            LOGGER.debug("Found auth header in request. It looks like this: {}", jws);
        }
        else if(authParam.isPresent()) {
            jws = authParam.get();
        }
        else {
            // If there's no token then we've nothing to do.
            return Optional.empty();
        }

        try {
            return Optional.of(verifyToken(jws));
        } catch (Exception e){
            LOGGER.error("Unable to verify token:", e.getMessage(), e);
            // If we get an exception verifying the token then we need to log the message
            // and continue as if the token wasn't provided.
            // TODO: decide if this should be handled by an exception and how
            return Optional.empty();
        }
    }

    public JWTAuthenticationToken verifyToken(String token) {
        //TODO: refactor to use Swagger API
        Client client = ClientBuilder.newClient(new ClientConfig().register(ClientResponse.class));
        Response response = client
            .target(this.authenticationServiceUrl + "/verify/" + token)
            .request()
            .get();
        String usersEmail = response.readEntity(String.class);
        return new JWTAuthenticationToken(usersEmail, token);
    }

    public static Optional<String> getAuthParam(ServletRequest request){
        String token = request.getParameter("token");
        LOGGER.debug("Found param token. It looks like this: {}", token);
        return Optional.ofNullable(token);
    }

    public static Optional<String> getAuthHeader(ServletRequest request) {
        HttpServletRequest httpServletRequest = WebUtils.toHttp(request);
        return(getAuthHeader(httpServletRequest));
    }

    public static Optional<String> getAuthHeader(HttpServletRequest httpServletRequest){
        String authHeader = httpServletRequest.getHeader(AUTHORIZATION_HEADER);
        return Strings.isNullOrEmpty(authHeader) ? Optional.empty() : Optional.of(authHeader);
    }


}
