package stroom.security.openid.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

@JsonInclude(Include.NON_NULL)
public class OpenIdConfigurationResponse {
    @JsonProperty("authorization_endpoint")
    @JsonPropertyDescription("The authentication endpoint used in OpenId authentication." +
            "Should only be set if useInternal is true.")
    private final String authorizationEndpoint;
    @JsonProperty("claims_supported")
    private final String[] claimsSupported;
    @JsonProperty("code_challenge_methods_supported")
    private final String[] codeChallengeMethodsSupported;
    @JsonProperty("device_authorization_endpoint")
    private final String deviceAuthorizationEndpoint;
    @JsonProperty("grant_types_supported")
    private final String[] grantTypesSupported;
    @JsonProperty("id_token_signing_alg_values_supported")
    private final String[] idTokenSigningSlgValuesSupported;
    @JsonProperty("issuer")
    @JsonPropertyDescription("The issuer used in OpenId authentication." +
            "Should only be set if useInternal is true.")
    private final String issuer;
    @JsonProperty("jwks_uri")
    @JsonPropertyDescription("The URI to obtain the JSON Web Key Set from in OpenId authentication." +
            "Should only be set if useInternal is true.")
    private final String jwksUri;
    @JsonProperty("response_types_supported")
    private final String[] responseTypesSupported;
    @JsonProperty("revocation_endpoint")
    private final String revocationEndpoint;
    @JsonProperty("scopes_supported")
    private final String[] scopesSupported;
    @JsonProperty("subject_types_supported")
    private final String[] subjectTypesSupported;
    @JsonProperty("token_endpoint")
    @JsonPropertyDescription("The token endpoint used in OpenId authentication." +
            "Should only be set if useInternal is true.")
    private final String tokenEndpoint;
    @JsonProperty("token_endpoint_auth_methods_supported")
    private final String[] tokenEndpointAuthMethodsSupported;
    @JsonProperty("userinfo_endpoint")
    private final String userinfoEndpoint;

    @JsonCreator
    OpenIdConfigurationResponse(@JsonProperty("authorization_endpoint") final String authorizationEndpoint,
                                @JsonProperty("claims_supported") final String[] claimsSupported,
                                @JsonProperty("code_challenge_methods_supported") final String[] codeChallengeMethodsSupported,
                                @JsonProperty("device_authorization_endpoint") final String deviceAuthorizationEndpoint,
                                @JsonProperty("grant_types_supported") final String[] grantTypesSupported,
                                @JsonProperty("id_token_signing_alg_values_supported") final String[] idTokenSigningSlgValuesSupported,
                                @JsonProperty("issuer") final String issuer,
                                @JsonProperty("jwks_uri") final String jwksUri,
                                @JsonProperty("response_types_supported") final String[] responseTypesSupported,
                                @JsonProperty("revocation_endpoint") final String revocationEndpoint,
                                @JsonProperty("scopes_supported") final String[] scopesSupported,
                                @JsonProperty("subject_types_supported") final String[] subjectTypesSupported,
                                @JsonProperty("token_endpoint") final String tokenEndpoint,
                                @JsonProperty("token_endpoint_auth_methods_supported") final String[] tokenEndpointAuthMethodsSupported,
                                @JsonProperty("userinfo_endpoint") final String userinfoEndpoint) {
        this.authorizationEndpoint = authorizationEndpoint;
        this.claimsSupported = claimsSupported;
        this.codeChallengeMethodsSupported = codeChallengeMethodsSupported;
        this.deviceAuthorizationEndpoint = deviceAuthorizationEndpoint;
        this.grantTypesSupported = grantTypesSupported;
        this.idTokenSigningSlgValuesSupported = idTokenSigningSlgValuesSupported;
        this.issuer = issuer;
        this.jwksUri = jwksUri;
        this.responseTypesSupported = responseTypesSupported;
        this.revocationEndpoint = revocationEndpoint;
        this.scopesSupported = scopesSupported;
        this.subjectTypesSupported = subjectTypesSupported;
        this.tokenEndpoint = tokenEndpoint;
        this.tokenEndpointAuthMethodsSupported = tokenEndpointAuthMethodsSupported;
        this.userinfoEndpoint = userinfoEndpoint;
    }

    public String getAuthorizationEndpoint() {
        return authorizationEndpoint;
    }

    public String[] getClaimsSupported() {
        return claimsSupported;
    }

    public String[] getCodeChallengeMethodsSupported() {
        return codeChallengeMethodsSupported;
    }

    public String getDeviceAuthorizationEndpoint() {
        return deviceAuthorizationEndpoint;
    }

    public String[] getGrantTypesSupported() {
        return grantTypesSupported;
    }

    public String[] getIdTokenSigningSlgValuesSupported() {
        return idTokenSigningSlgValuesSupported;
    }

    public String getIssuer() {
        return issuer;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public String[] getResponseTypesSupported() {
        return responseTypesSupported;
    }

    public String getRevocationEndpoint() {
        return revocationEndpoint;
    }

    public String[] getScopesSupported() {
        return scopesSupported;
    }

    public String[] getSubjectTypesSupported() {
        return subjectTypesSupported;
    }

    public String getTokenEndpoint() {
        return tokenEndpoint;
    }

    public String[] getTokenEndpointAuthMethodsSupported() {
        return tokenEndpointAuthMethodsSupported;
    }

    public String getUserinfoEndpoint() {
        return userinfoEndpoint;
    }

    public static class Builder {
        private String authorizationEndpoint;
        private String[] claimsSupported;
        private String[] codeChallengeMethodsSupported;
        private String deviceAuthorizationEndpoint;
        private String[] grantTypesSupported;
        private String[] idTokenSigningSlgValuesSupported;
        private String issuer;
        private String jwksUri;
        private String[] responseTypesSupported;
        private String revocationEndpoint;
        private String[] scopesSupported;
        private String[] subjectTypesSupported;
        private String tokenEndpoint;
        private String[] tokenEndpointAuthMethodsSupported;
        private String userinfoEndpoint;

        public Builder authorizationEndpoint(final String authorizationEndpoint) {
            this.authorizationEndpoint = authorizationEndpoint;
            return this;
        }

        public Builder claimsSupported(final String[] claimsSupported) {
            this.claimsSupported = claimsSupported;
            return this;
        }

        public Builder codeChallengeMethodsSupported(final String[] codeChallengeMethodsSupported) {
            this.codeChallengeMethodsSupported = codeChallengeMethodsSupported;
            return this;
        }

        public Builder deviceAuthorizationEndpoint(final String deviceAuthorizationEndpoint) {
            this.deviceAuthorizationEndpoint = deviceAuthorizationEndpoint;
            return this;
        }

        public Builder grantTypesSupported(final String[] grantTypesSupported) {
            this.grantTypesSupported = grantTypesSupported;
            return this;
        }

        public Builder idTokenSigningSlgValuesSupported(final String[] idTokenSigningSlgValuesSupported) {
            this.idTokenSigningSlgValuesSupported = idTokenSigningSlgValuesSupported;
            return this;
        }

        public Builder issuer(final String issuer) {
            this.issuer = issuer;
            return this;
        }

        public Builder jwksUri(final String jwksUri) {
            this.jwksUri = jwksUri;
            return this;
        }

        public Builder responseTypesSupported(final String[] responseTypesSupported) {
            this.responseTypesSupported = responseTypesSupported;
            return this;
        }

        public Builder revocationEndpoint(final String revocationEndpoint) {
            this.revocationEndpoint = revocationEndpoint;
            return this;
        }

        public Builder scopesSupported(final String[] scopesSupported) {
            this.scopesSupported = scopesSupported;
            return this;
        }

        public Builder subjectTypesSupported(final String[] subjectTypesSupported) {
            this.subjectTypesSupported = subjectTypesSupported;
            return this;
        }

        public Builder tokenEndpoint(final String tokenEndpoint) {
            this.tokenEndpoint = tokenEndpoint;
            return this;
        }

        public Builder tokenEndpointAuthMethodsSupported(final String[] tokenEndpointAuthMethodsSupported) {
            this.tokenEndpointAuthMethodsSupported = tokenEndpointAuthMethodsSupported;
            return this;
        }

        public Builder userinfoEndpoint(final String userinfoEndpoint) {
            this.userinfoEndpoint = userinfoEndpoint;
            return this;
        }

        public OpenIdConfigurationResponse build() {
            return new OpenIdConfigurationResponse(
                    authorizationEndpoint,
                    claimsSupported,
                    codeChallengeMethodsSupported,
                    deviceAuthorizationEndpoint,
                    grantTypesSupported,
                    idTokenSigningSlgValuesSupported,
                    issuer,
                    jwksUri,
                    responseTypesSupported,
                    revocationEndpoint,
                    scopesSupported,
                    subjectTypesSupported,
                    tokenEndpoint,
                    tokenEndpointAuthMethodsSupported,
                    userinfoEndpoint);
        }
    }
}