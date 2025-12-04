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

package stroom.util.http;

import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.Objects;

/**
 * Represents a configuration of credentials for either Username Password or NT credentials
 * <p/>
 * <b>Configuration Parameters:</b>
 * <table>
 *     <tr>
 *         <td>Name</td>
 *         <td>Default</td>
 *         <td>Description</td>
 *     </tr>
 *     <tr>
 *         <td>{@code username}</td>
 *         <td>REQUIRED</td>
 *         <td>The username used to connect to the server.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code password}</td>
 *         <td>REQUIRED</td>
 *         <td>The password used to connect to the server.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code authScheme}</td>
 *         <td>null</td>
 *         <td>Optional, The authentication scheme used by the underlying
 *         {@link org.apache.hc.client5.http.auth.AuthScope} class. Can be one of:<ul>
 *         <li>Basic</li><li>NTLM</li></ul></td>
 *     </tr>
 *     <tr>
 *         <td>{@code realm}</td>
 *         <td>null</td>
 *         <td>Optional, Realm to be used for NTLM Authentication.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code hostname}</td>
 *         <td>null</td>
 *         <td>The hostname of the Principal in NTLM Authentication.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code domain}</td>
 *         <td>null</td>
 *         <td>Optional, The domain used in NTLM Authentication.</td>
 *     </tr>
 *     <tr>
 *         <td>{@code credentialType}</td>
 *         <td>null</td>
 *         <td>The {@link org.apache.hc.client5.http.auth.Credentials} implementation
 *         to use for proxy authentication. Currently supports
 *         UsernamePassword ({@link org.apache.hc.client5.http.auth.UsernamePasswordCredentials}) and
 *         NT ({@link org.apache.hc.client5.http.auth.NTCredentials})</td>
 *     </tr>
 * </table>
 */
@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class HttpAuthConfiguration extends AbstractConfig implements IsStroomConfig, IsProxyConfig {

    public static final String BASIC_AUTH_SCHEME = "Basic";

    public static final String NTLM_AUTH_SCHEME = "NTLM";

    public static final String USERNAME_PASSWORD_CREDS = "UsernamePassword";

    public static final String NT_CREDS = "NT";

    @NotEmpty
    private final String username;

    @NotEmpty
    private final String password;

    @Pattern(regexp = BASIC_AUTH_SCHEME + "|" + NTLM_AUTH_SCHEME)
    private final String authScheme;

    private final String realm;

    private final String hostname;

    private final String domain;

    @Pattern(regexp = USERNAME_PASSWORD_CREDS + "|" + NT_CREDS, flags = {Pattern.Flag.CASE_INSENSITIVE})
    private final String credentialType;

    public HttpAuthConfiguration() {
        username = "";
        password = "";
        authScheme = null;
        realm = null;
        hostname = null;
        domain = null;
        credentialType = null;
    }

    @JsonCreator
    public HttpAuthConfiguration(@JsonProperty("username") final String username,
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

    @JsonProperty
    public String getUsername() {
        return username;
    }

    @JsonProperty
    public String getPassword() {
        return password;
    }

    @JsonProperty
    public String getAuthScheme() {
        return authScheme;
    }

    @JsonProperty
    public String getRealm() {
        return realm;
    }

    @JsonProperty
    public String getHostname() {
        return hostname;
    }

    @JsonProperty
    public String getDomain() {
        return domain;
    }

    @JsonProperty
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
        final HttpAuthConfiguration that = (HttpAuthConfiguration) o;
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
        return "HttpAuthConfiguration{" +
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
        return new Builder(new HttpAuthConfiguration());
    }

    public static class Builder extends AbstractBuilder<HttpAuthConfiguration, HttpAuthConfiguration.Builder> {

        private String username;
        private String password;
        private String authScheme;
        private String realm;
        private String hostname;
        private String domain;
        private String credentialType;

        public Builder() {
            this(new HttpAuthConfiguration());
        }

        public Builder(final HttpAuthConfiguration httpAuthConfiguration) {
            username = httpAuthConfiguration.username;
            password = httpAuthConfiguration.password;
            authScheme = httpAuthConfiguration.authScheme;
            realm = httpAuthConfiguration.realm;
            hostname = httpAuthConfiguration.hostname;
            domain = httpAuthConfiguration.domain;
            credentialType = httpAuthConfiguration.credentialType;
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

        public HttpAuthConfiguration build() {
            return new HttpAuthConfiguration(
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
