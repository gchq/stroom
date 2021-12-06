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

package stroom.security.identity.config;

import stroom.util.config.annotations.Password;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.NotInjectableConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.simplejavamail.mailer.config.TransportStrategy;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class SmtpConfig extends AbstractConfig {

    @NotNull
    @JsonProperty("host")
    @JsonPropertyDescription("The fully qualified hostname of the SMTP server.")
    private final String host;

    @Min(0)
    @Max(65535)
    @JsonProperty("port")
    @JsonPropertyDescription("The port for the SMTP server.")
    private final int port;

    @NotNull
    @JsonProperty("transport")
    @JsonPropertyDescription("The transport type for communicating with the SMTP server.")
    private final String transport;

    @JsonProperty("username")
    @JsonPropertyDescription("The username to authenticate with on the SMTP server.")
    private final String username;

    @Password
    @JsonProperty("password")
    @JsonPropertyDescription("The password to authenticate with on the SMTP server.")
    private final String password;

    public SmtpConfig() {
        host = "localhost";
        port = 2525;
        transport = "plain";
        password = null;
        username = null;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SmtpConfig(@JsonProperty("host") final String host,
                      @JsonProperty("port") final int port,
                      @JsonProperty("transport") final String transport,
                      @JsonProperty("username") final String username,
                      @JsonProperty("password") final String password) {
        this.host = host;
        this.port = port;
        this.transport = transport;
        this.username = username;
        this.password = password;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getTransport() {
        return transport;
    }

    @JsonIgnore
    public TransportStrategy getTransportStrategy() {
        switch (transport) {
            case "TLS":
                return TransportStrategy.SMTP_TLS;
            case "SSL":
                return TransportStrategy.SMTP_TLS;
            case "plain":
                return TransportStrategy.SMTP_PLAIN;
            default:
                return TransportStrategy.SMTP_PLAIN;
        }
    }

    @Override
    public String toString() {
        return "SmtpConfig{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", transport='" + transport + '\'' +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
