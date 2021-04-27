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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.simplejavamail.mailer.config.TransportStrategy;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@NotInjectableConfig
public class SmtpConfig extends AbstractConfig {

    @NotNull
    @JsonProperty("host")
    @JsonPropertyDescription("The fully qualified hostname of the SMTP server.")
    private String host = "localhost";

    @Min(0)
    @Max(65535)
    @JsonProperty("port")
    @JsonPropertyDescription("The port for the SMTP server.")
    private int port = 2525;

    @NotNull
    @JsonProperty("transport")
    @JsonPropertyDescription("The transport type for communicating with the SMTP server.")
    private String transport = "plain";

    @JsonProperty("username")
    @JsonPropertyDescription("The username to authenticate with on the SMTP server.")
    private String username;

    @Password
    @JsonProperty("password")
    @JsonPropertyDescription("The password to authenticate with on the SMTP server.")
    private String password;

    public String getHost() {
        return host;
    }

    public void setHost(final String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(final int port) {
        this.port = port;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(final String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(final String password) {
        this.password = password;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(final String transport) {
        this.transport = transport;
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
}
