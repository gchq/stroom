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

package stroom.util.shared.http;

import stroom.util.shared.AbstractBuilder;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class HttpAuthConfig {

    private static final String BASIC_AUTH_SCHEME = "Basic";
    private static final String NTLM_AUTH_SCHEME = "NTLM";
    private static final String USERNAME_PASSWORD_CREDS = "UsernamePassword";
    private static final String NT_CREDS = "NT";

    @JsonProperty
    private final String username;
    @JsonProperty
    private final String password;
    @JsonProperty
    private final String authScheme;
    @JsonProperty
    private final String realm;
    @JsonProperty
    private final String hostname;
    @JsonProperty
    private final String domain;
    @JsonProperty
    private final String credentialType;

    @JsonCreator
    public HttpAuthConfig(@JsonProperty("username") final String username,
                          @JsonProperty("password") final String password,
                          @JsonProperty("authScheme") final String authScheme,
                          @JsonProperty("realm") final String realm,
                          @JsonProperty("hostname") final String hostname,
                          @JsonProperty("domain") final String domain,
                          @JsonProperty("credentialType") final String credentialType) {
        this.username = username;
        this.password = password;
        this.authScheme = authScheme;
        this.realm = realm;
        this.hostname = hostname;
        this.domain = domain;
        this.credentialType = credentialType;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getAuthScheme() {
        return authScheme;
    }

    public String getRealm() {
        return realm;
    }

    public String getHostname() {
        return hostname;
    }

    public String getDomain() {
        return domain;
    }

    public String getCredentialType() {
        return credentialType;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final HttpAuthConfig that = (HttpAuthConfig) o;
        return Objects.equals(username, that.username) &&
               Objects.equals(password, that.password) &&
               Objects.equals(authScheme, that.authScheme) &&
               Objects.equals(realm, that.realm) &&
               Objects.equals(hostname, that.hostname) &&
               Objects.equals(domain, that.domain) &&
               Objects.equals(credentialType, that.credentialType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(username, password, authScheme, realm, hostname, domain, credentialType);
    }

    @Override
    public String toString() {
        return "HttpAuthConfig{" +
               "username='" + username + '\'' +
               ", password='" + password + '\'' +
               ", authScheme='" + authScheme + '\'' +
               ", realm='" + realm + '\'' +
               ", hostname='" + hostname + '\'' +
               ", domain='" + domain + '\'' +
               ", credentialType='" + credentialType + '\'' +
               '}';
    }


    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder extends AbstractBuilder<HttpAuthConfig, Builder> {

        private String username = "";
        private String password = "";
        private String authScheme;
        private String realm;
        private String hostname;
        private String domain;
        private String credentialType;

        private Builder() {
        }

        private Builder(final HttpAuthConfig httpAuthConfig) {
            username = httpAuthConfig.username;
            password = httpAuthConfig.password;
            authScheme = httpAuthConfig.authScheme;
            realm = httpAuthConfig.realm;
            hostname = httpAuthConfig.hostname;
            domain = httpAuthConfig.domain;
            credentialType = httpAuthConfig.credentialType;
        }

        public Builder username(final String username) {
            this.username = username;
            return self();
        }

        public Builder password(final String password) {
            this.password = password;
            return self();
        }

        public Builder authScheme(final String authScheme) {
            this.authScheme = authScheme;
            return self();
        }

        public Builder realm(final String realm) {
            this.realm = realm;
            return self();
        }

        public Builder hostname(final String hostname) {
            this.hostname = hostname;
            return self();
        }

        public Builder domain(final String domain) {
            this.domain = domain;
            return self();
        }

        public Builder credentialType(final String credentialType) {
            this.credentialType = credentialType;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        public HttpAuthConfig build() {
            return new HttpAuthConfig(
                    username,
                    password,
                    authScheme,
                    realm,
                    hostname,
                    domain,
                    credentialType);
        }
    }
}
