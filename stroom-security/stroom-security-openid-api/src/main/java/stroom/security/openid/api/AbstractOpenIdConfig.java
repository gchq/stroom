/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.openid.api;

import stroom.util.collections.CollectionUtil;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NullSafe;
import stroom.util.shared.validation.AllMatchPattern;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import io.dropwizard.validation.ValidationMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
public abstract class AbstractOpenIdConfig
        extends AbstractConfig
        implements OpenIdConfiguration {

    public static final String PROP_NAME_CLIENT_ID = "clientId";
    public static final String PROP_NAME_CLIENT_SECRET = "clientSecret";
    public static final String PROP_NAME_CONFIGURATION_ENDPOINT = "openIdConfigurationEndpoint";
    public static final String PROP_NAME_IDP_TYPE = "identityProviderType";
    public static final String PROP_NAME_EXPECTED_SIGNER_PREFIXES = "expectedSignerPrefixes";
    public static final String DEFAULT_POST_LOGOUT_REDIRECT_URI = OpenId.POST_LOGOUT_REDIRECT_URI;
    public static final List<String> DEFAULT_REQUEST_SCOPES = OpenId.DEFAULT_REQUEST_SCOPES;
    public static final List<String> DEFAULT_CLIENT_CREDENTIALS_SCOPES = OpenId.DEFAULT_CLIENT_CREDENTIALS_SCOPES;
    public static final String DEFAULT_CLAIM_SUBJECT = OpenId.CLAIM__SUBJECT;
    public static final String DEFAULT_CLAIM_PREFERRED_USERNAME = OpenId.CLAIM__PREFERRED_USERNAME;
    public static final boolean DEFAULT_FORM_TOKEN_REQUEST = true;
    public static final boolean DEFAULT_VALIDATE_AUDIENCE = true;
    public static final boolean DEFAULT_AUDIENCE_CLAIM_REQUIRED = false;
    public static final String DEFAULT_FULL_NAME_CLAIM_TEMPLATE = "${name}";
    public static final String DEFAULT_AWS_PUBLIC_KEY_URI_TEMPLATE =
            "https://public-keys.auth.elb.${awsRegion}.amazonaws.com/${keyId}";

    private final IdpType identityProviderType;

    /**
     * e.g. https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/openid-configuration
     * e.g. https://accounts.google.com/.well-known/openid-configuration
     */
    private final String openIdConfigurationEndpoint;

    /**
     * Don't set if using configuration endpoint
     * e.g. stroom
     * e.g. accounts.google.com
     */
    private final String issuer;

    /**
     * Don't set if using configuration endpoint
     * e.g. https://mydomain.auth.us-east-1.amazoncognito.com/oauth2/authorize
     * e.g. https://accounts.google.com/o/oauth2/v2/auth
     */
    private final String authEndpoint;

    /**
     * Don't set if using configuration endpoint
     * e.g. https://mydomain.auth.us-east-1.amazoncognito.com/oauth2/token
     * e.g. https://accounts.google.com/o/oauth2/token
     */
    private final String tokenEndpoint;

    /**
     * Don't set if using configuration endpoint
     * e.g. https://cognito-idp.{region}.amazonaws.com/{userPoolId}/.well-known/jwks.json
     * e.g. https://www.googleapis.com/oauth2/v3/certs
     */
    private final String jwksUri;

    /**
     * Not provided by the configuration endpoint, must be configured manually.
     * <p>
     * e.g. https://mydomain.auth.us-east-1.amazoncognito.com/logout
     * e.g. https://www.google.com/accounts/Logout?
     * continue=https://appengine.google.com/_ah/logout?continue=http://www.example.com"
     */
    private final String logoutEndpoint;

    /**
     * Not provided by the configuration endpoint, must be configured manually.
     * <p>
     * The name of the URI parameter to use when passing the logout redirect URI to the IDP.
     * This is here as the spec seems to have changed from 'redirect_uri' to 'post_logout_redirect_uri'.
     */
    private final String logoutRedirectParamName;

    /**
     * Some OpenId providers, e.g. AWS Cognito, require a form to be used for token requests.
     */
    private final boolean formTokenRequest;

    /**
     * The client ID used in OpenId authentication.
     */
    private final String clientId;

    /**
     * The client secret used in OpenId authentication.
     */
    private final String clientSecret;

    /**
     * If custom auth flow request scopes are required then this should be set to replace the defaults
     * of 'openid' and 'email'.
     */
    private final List<String> requestScopes;

    /**
     * If custom scopes are required for client_credentials requests then this should be set to replace the default
     * of 'openid'.
     */
    private final List<String> clientCredentialsScopes;

    /**
     * A set of audience claim values, one of which must appear in the audience claim in the token.
     * If empty, no validation will be performed on the audience claim.
     * If audienceClaimRequired is false and there is no audience claim in the token, then allowedAudiences
     * will be ignored.
     */
    private final Set<String> allowedAudiences;

    /**
     * If true the token will fail validation if the audience claim is not present and allowedAudiences is not empty.
     */
    private final boolean audienceClaimRequired;

    private final Set<String> validIssuers;

    /**
     * The OIDC claim to use to uniquely identify the user on the IDP. This id will be used to link an IDP
     * user to the stroom user. It must be unique on the IDP and not subject to change. Be default is 'sub'.
     */
    private final String uniqueIdentityClaim;

    /**
     * This is a more human friendly username for the user. It is not used for linking IDP to stroom users so
     * may not be unique on the IDP, and may change. By default, it is 'preferred_username'.
     */
    private final String userDisplayNameClaim;

    private final String fullNameClaimTemplate;

    private final Set<String> expectedSignerPrefixes;

    private final String publicKeyUriPattern;

    public AbstractOpenIdConfig() {
        identityProviderType = getDefaultIdpType();
        openIdConfigurationEndpoint = null;
        issuer = null;
        authEndpoint = null;
        tokenEndpoint = null;
        jwksUri = null;
        logoutEndpoint = null;
        logoutRedirectParamName = DEFAULT_POST_LOGOUT_REDIRECT_URI;
        formTokenRequest = DEFAULT_FORM_TOKEN_REQUEST;
        clientSecret = null;
        clientId = null;
        requestScopes = DEFAULT_REQUEST_SCOPES;
        clientCredentialsScopes = DEFAULT_CLIENT_CREDENTIALS_SCOPES;
        allowedAudiences = Collections.emptySet();
        audienceClaimRequired = DEFAULT_AUDIENCE_CLAIM_REQUIRED;
        validIssuers = Collections.emptySet();
        uniqueIdentityClaim = DEFAULT_CLAIM_SUBJECT;
        userDisplayNameClaim = DEFAULT_CLAIM_PREFERRED_USERNAME;
        fullNameClaimTemplate = DEFAULT_FULL_NAME_CLAIM_TEMPLATE;
        expectedSignerPrefixes = Collections.emptySet();
        publicKeyUriPattern = DEFAULT_AWS_PUBLIC_KEY_URI_TEMPLATE;
    }

    @JsonIgnore
    public abstract IdpType getDefaultIdpType();

    @JsonCreator
    public AbstractOpenIdConfig(
            @JsonProperty(PROP_NAME_IDP_TYPE) final IdpType identityProviderType,
            @JsonProperty(PROP_NAME_CONFIGURATION_ENDPOINT) final String openIdConfigurationEndpoint,
            @JsonProperty("issuer") final String issuer,
            @JsonProperty("authEndpoint") final String authEndpoint,
            @JsonProperty("tokenEndpoint") final String tokenEndpoint,
            @JsonProperty("jwksUri") final String jwksUri,
            @JsonProperty("logoutEndpoint") final String logoutEndpoint,
            @JsonProperty("logoutRedirectParamName") final String logoutRedirectParamName,
            @JsonProperty("formTokenRequest") final Boolean formTokenRequest,
            @JsonProperty("clientId") final String clientId,
            @JsonProperty("clientSecret") final String clientSecret,
            @JsonProperty("requestScopes") final List<String> requestScopes,
            @JsonProperty("clientCredentialsScopes") final List<String> clientCredentialsScopes,
            @JsonProperty("allowedAudiences") final Set<String> allowedAudiences,
            @JsonProperty("audienceClaimRequired") final Boolean audienceClaimRequired,
            @JsonProperty("validIssuers") final Set<String> validIssuers,
            @JsonProperty("uniqueIdentityClaim") final String uniqueIdentityClaim,
            @JsonProperty("userDisplayNameClaim") final String userDisplayNameClaim,
            @JsonProperty("fullNameClaimTemplate") final String fullNameClaimTemplate,
            @JsonProperty(PROP_NAME_EXPECTED_SIGNER_PREFIXES) final Set<String> expectedSignerPrefixes,
            @JsonProperty("publicKeyUriPattern") final String publicKeyUriPattern) {

        this.identityProviderType = Objects.requireNonNullElseGet(identityProviderType, this::getDefaultIdpType);
        this.openIdConfigurationEndpoint = openIdConfigurationEndpoint;
        this.issuer = issuer;
        this.authEndpoint = authEndpoint;
        this.tokenEndpoint = tokenEndpoint;
        this.jwksUri = jwksUri;
        this.logoutEndpoint = logoutEndpoint;
        this.logoutRedirectParamName = Objects.requireNonNullElse(
                logoutRedirectParamName, DEFAULT_POST_LOGOUT_REDIRECT_URI);
        this.formTokenRequest = Objects.requireNonNullElse(formTokenRequest, DEFAULT_FORM_TOKEN_REQUEST);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.requestScopes = Objects.requireNonNullElse(requestScopes, DEFAULT_REQUEST_SCOPES);
        this.clientCredentialsScopes = Objects.requireNonNullElse(
                clientCredentialsScopes, DEFAULT_CLIENT_CREDENTIALS_SCOPES);
        this.allowedAudiences = CollectionUtil.cleanItems(allowedAudiences, String::trim);
        this.audienceClaimRequired = Objects.requireNonNullElse(audienceClaimRequired, DEFAULT_AUDIENCE_CLAIM_REQUIRED);
        this.validIssuers = NullSafe.set(validIssuers);
        this.uniqueIdentityClaim = uniqueIdentityClaim;
        this.userDisplayNameClaim = userDisplayNameClaim;
        this.fullNameClaimTemplate = NullSafe.nonBlankStringElse(
                fullNameClaimTemplate, DEFAULT_FULL_NAME_CLAIM_TEMPLATE);
        this.expectedSignerPrefixes = NullSafe.set(expectedSignerPrefixes);
        this.publicKeyUriPattern = publicKeyUriPattern;
    }

    /**
     * @return The type of Open ID Connnect identity provider in use.
     */
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The type of Open ID Connect identity provider that stroom/proxy" +
                             "will use for authentication. Valid values are: " +
                             "INTERNAL_IDP - Stroom's own built in IDP (not valid for stroom-proxy)," +
                             "EXTERNAL_IDP - An external IDP such as KeyCloak/Cognito (stroom's internal IDP can be " +
                             "used as stroom-proxy's external IDP) and" +
                             "TEST_CREDENTIALS - Use hard-coded authentication credentials for test/demo only. " +
                             "Changing this property will require a restart of the application.")
    public IdpType getIdentityProviderType() {
        return identityProviderType;
    }

    @Override
    @JsonPropertyDescription("You can set an openid-configuration URL to automatically configure much of the openid " +
                             "settings. Without this the other endpoints etc must be set manually.")
    @JsonProperty
    public String getOpenIdConfigurationEndpoint() {
        return openIdConfigurationEndpoint;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("The issuer used in OpenId authentication." +
                             "Should only be set if not using a configuration endpoint.")
    public String getIssuer() {
        return issuer;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("The authentication endpoint used in OpenId authentication." +
                             "Should only be set if not using a configuration endpoint.")
    public String getAuthEndpoint() {
        return authEndpoint;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("The token endpoint used in OpenId authentication." +
                             "Should only be set if not using a configuration endpoint.")
    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("The URI to obtain the JSON Web Key Set from in OpenId authentication." +
                             "Should only be set if not using a configuration endpoint.")
    public String getJwksUri() {
        return jwksUri;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("The logout endpoint for the identity provider." +
                             "This is not typically provided by the configuration endpoint.")
    public String getLogoutEndpoint() {
        return logoutEndpoint;
    }

    @Override
    @Pattern(regexp = "(" + OpenId.REDIRECT_URI + "|" + OpenId.POST_LOGOUT_REDIRECT_URI + ")")
    @JsonProperty
    @JsonPropertyDescription("The name of the URI parameter to use when passing the logout redirect URI to the IDP. "
                             + "This is here as the spec seems to have changed from 'redirect_uri' to " +
                             "'post_logout_redirect_uri'.")
    public String getLogoutRedirectParamName() {
        return logoutRedirectParamName;
    }

    @Override
    @JsonProperty(PROP_NAME_CLIENT_ID)
    @JsonPropertyDescription("The client ID used in OpenId authentication.")
    public String getClientId() {
        return clientId;
    }

    // May be null for mTLS auth
    @Override
    @JsonProperty(PROP_NAME_CLIENT_SECRET)
    @JsonPropertyDescription("The client secret used in OpenId authentication.")
    public String getClientSecret() {
        return clientSecret;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("Some OpenId providers, e.g. AWS Cognito, require a form to be used for token requests.")
    public boolean isFormTokenRequest() {
        return formTokenRequest;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("If custom auth flow request scopes are required then this should be set to replace " +
                             "the defaults of 'openid' and 'email'.")
    public List<String> getRequestScopes() {
        return requestScopes;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("If custom scopes are required for client_credentials requests then this should be " +
                             "set to replace the default of 'openid'. E.g. for Azure AD you will likely need to set " +
                             "this to 'openid' and '<your-app-id-uri>/.default>'.")
    public List<String> getClientCredentialsScopes() {
        return clientCredentialsScopes;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("A set of audience claim values, one of which must appear in the audience " +
                             "claim in the token. " +
                             "If empty, no validation will be performed on the audience claim." +
                             "If audienceClaimRequired is false and there is no audience claim in the token, " +
                             "then allowedAudiences will be ignored.")
    public Set<String> getAllowedAudiences() {
        return allowedAudiences;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("If true the token will fail validation if the audience claim is not present " +
                             "and allowedAudiences is not empty.")
    public boolean isAudienceClaimRequired() {
        return audienceClaimRequired;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("A set of issuers (in addition to the 'issuer' property that is provided by the IDP " +
                             "that are deemed valid when seen in a token. If no additional valid issuers are " +
                             "required then set this to an empty set. Also this is used to validate the 'issuer' " +
                             "returned by the IDP when it is not a sub path of 'openIdConfigurationEndpoint'. If " +
                             "this set is empty then Stroom will verify that the ")
    public Set<String> getValidIssuers() {
        return validIssuers;
    }

    @Override
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The Open ID Connect claim used to link an identity on the IDP to a stroom user. " +
                             "Must uniquely identify the user on the IDP and not be subject to change. Uses 'sub' by " +
                             "default.")
    public String getUniqueIdentityClaim() {
        return uniqueIdentityClaim;
    }

    @Override
    @NotNull
    @JsonProperty
    @JsonPropertyDescription("The Open ID Connect claim used to provide a more human friendly username for a user " +
                             "than that provided by uniqueIdentityClaim. It is not guaranteed to be unique and may " +
                             "change.")
    public String getUserDisplayNameClaim() {
        return userDisplayNameClaim;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("A template to build the user's full name using claim values as variables in the " +
                             "template. E.g '${firstName} ${lastName}' or '${name}'. " +
                             "If this property is set in the YAML file, use single quotes to prevent the " +
                             "variables being expanded when the config file is loaded.")
    public String getFullNameClaimTemplate() {
        return fullNameClaimTemplate;
    }

    // A fairly basic pattern to ensure we get enough of an ARN, i.e.
    // arn:aws:elasticloadbalancing:region-code:account-id:
    // I.e. limit signers down to at least any ELB in an account
    // See https://docs.aws.amazon.com/IAM/latest/UserGuide/reference-arns.html
    @AllMatchPattern(pattern = "^arn:[^:\\s]+:[^:\\s]+:[^:\\s]+:[^:\\s]+:\\S*$")
    @Override
    @SuppressWarnings("checkstyle:lineLength")
    @JsonProperty
    @JsonPropertyDescription("If using an AWS load balancer to handle the authentication, set this to the Amazon " +
                             "Resource Names (ARN) of the load balancer(s) fronting stroom, which will be something " +
                             "like 'arn:aws:elasticloadbalancing:region-code:account-id:loadbalancer" +
                             "/app/load-balancer-name/load-balancer-id'. " +
                             "This config value will be used to verify the 'signer' in the JWT header. " +
                             "Each value is the first N characters of the ARN and as a minimum must include up to " +
                             "the colon after the account-id, i.e. " +
                             "'arn:aws:elasticloadbalancing:region-code:account-id:'." +
                             "See https://docs.aws.amazon.com/elasticloadbalancing/" +
                             "latest/application/listener-authenticate-users.html#user-claims-encoding")
    public Set<String> getExpectedSignerPrefixes() {
        return expectedSignerPrefixes;
    }

    @Override
    @JsonProperty
    @JsonPropertyDescription("If the token is signed by AWS then use this pattern to form the URI to obtain the " +
                             "public key from. The pattern supports the variables '${awsRegion}' and '${keyId}'. " +
                             "Multiple instances of a variable are also supported. " +
                             "If this property is set in the YAML file, use single quotes to prevent the " +
                             "variables being expanded when the config file is loaded.")
    public String getPublicKeyUriPattern() {
        return publicKeyUriPattern;
    }

    @JsonIgnore
    @SuppressWarnings("unused")
    @ValidationMethod(message = "If " + PROP_NAME_IDP_TYPE + " is set to 'EXTERNAL', property "
                                + PROP_NAME_CONFIGURATION_ENDPOINT + " must be set.")
    public boolean isConfigurationEndpointValid() {
        return !IdpType.EXTERNAL_IDP.equals(identityProviderType)
               || (openIdConfigurationEndpoint != null && !openIdConfigurationEndpoint.isBlank());
    }

    @Override
    public String toString() {
        return "OpenIdConfig{" +
               "identityProviderType=" + identityProviderType +
               ", openIdConfigurationEndpoint='" + openIdConfigurationEndpoint + '\'' +
               ", issuer='" + issuer + '\'' +
               ", authEndpoint='" + authEndpoint + '\'' +
               ", tokenEndpoint='" + tokenEndpoint + '\'' +
               ", jwksUri='" + jwksUri + '\'' +
               ", logoutEndpoint='" + logoutEndpoint + '\'' +
               ", logoutRedirectParamName='" + logoutRedirectParamName + '\'' +
               ", formTokenRequest=" + formTokenRequest +
               ", clientId='" + clientId + '\'' +
               ", clientSecret='" + clientSecret + '\'' +
               ", requestScopes='" + requestScopes + '\'' +
               ", allowedAudiences=" + allowedAudiences +
               ", audienceClaimRequired=" + audienceClaimRequired +
               ", uniqueIdentityClaim=" + uniqueIdentityClaim +
               ", userDisplayNameClaim=" + userDisplayNameClaim +
               ", fullNameClaimTemplate=" + fullNameClaimTemplate +
               ", expectedSignerPrefixes=" + expectedSignerPrefixes +
               '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractOpenIdConfig that = (AbstractOpenIdConfig) o;
        return formTokenRequest == that.formTokenRequest &&
               audienceClaimRequired == that.audienceClaimRequired &&
               identityProviderType == that.identityProviderType &&
               Objects.equals(openIdConfigurationEndpoint, that.openIdConfigurationEndpoint) &&
               Objects.equals(issuer, that.issuer) &&
               Objects.equals(authEndpoint, that.authEndpoint) &&
               Objects.equals(tokenEndpoint, that.tokenEndpoint) &&
               Objects.equals(jwksUri, that.jwksUri) &&
               Objects.equals(logoutEndpoint, that.logoutEndpoint) &&
               Objects.equals(logoutRedirectParamName, that.logoutRedirectParamName) &&
               Objects.equals(clientId, that.clientId) &&
               Objects.equals(clientSecret, that.clientSecret) &&
               Objects.equals(requestScopes, that.requestScopes) &&
               Objects.equals(allowedAudiences, that.allowedAudiences) &&
               Objects.equals(uniqueIdentityClaim, that.uniqueIdentityClaim) &&
               Objects.equals(userDisplayNameClaim, that.userDisplayNameClaim) &&
               Objects.equals(fullNameClaimTemplate, that.fullNameClaimTemplate) &&
               Objects.equals(expectedSignerPrefixes, that.expectedSignerPrefixes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                identityProviderType,
                openIdConfigurationEndpoint,
                issuer,
                authEndpoint,
                tokenEndpoint,
                jwksUri,
                logoutEndpoint,
                logoutRedirectParamName,
                formTokenRequest,
                clientId,
                clientSecret,
                requestScopes,
                allowedAudiences,
                audienceClaimRequired,
                uniqueIdentityClaim,
                userDisplayNameClaim,
                fullNameClaimTemplate,
                expectedSignerPrefixes);
    }
}
