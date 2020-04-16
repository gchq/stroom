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

package stroom.authentication.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.simplejavamail.mailer.config.TransportStrategy;
import stroom.util.shared.AbstractConfig;

import javax.validation.constraints.NotNull;

public class SmtpConfig extends AbstractConfig {
    @NotNull
    @JsonProperty
    private String host = "localhost";

    @NotNull
    @JsonProperty
    private int port = 2525;

    @NotNull
    @JsonProperty
    private String transport = "plain";

    @NotNull
    @JsonProperty
    private String username = "username";

    @NotNull
    @JsonProperty
    private String password = "password";

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
