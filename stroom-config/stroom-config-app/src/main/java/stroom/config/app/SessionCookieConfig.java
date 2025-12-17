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

package stroom.config.app;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.http.HttpCookie.SameSite;


@JsonPropertyOrder(alphabetic = true)
public class SessionCookieConfig extends AbstractConfig implements IsStroomConfig {

    @JsonProperty
    @JsonPropertyDescription("Marks the session cookies with the secure flag, indicating they " +
                             "should only be transmitted over a secure connection.")
    private final boolean secure;

    @JsonProperty
    @JsonPropertyDescription("Marks the session cookies as 'HttpOnly' so that we are inaccessible " +
                             "to client-side javascript code.")
    private final boolean httpOnly;

    @JsonProperty
    @JsonPropertyDescription("The same site attribute for the cookie, e.g. \"Strict\", \"Lax\" or \"None\".")
    private final SameSite sameSite;

    public SessionCookieConfig() {
        secure = true;
        httpOnly = true;
        sameSite = SameSite.STRICT;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SessionCookieConfig(@JsonProperty("secure") final boolean secure,
                               @JsonProperty("httpOnly") final boolean httpOnly,
                               @JsonProperty("sameSite") final SameSite sameSite) {
        this.secure = secure;
        this.httpOnly = httpOnly;
        this.sameSite = sameSite;
    }

    public boolean isSecure() {
        return secure;
    }

    public boolean isHttpOnly() {
        return httpOnly;
    }

    public SameSite getSameSite() {
        return sameSite;
    }

    @Override
    public String toString() {
        return "SessionCookieConfig{" +
               "secure=" + secure +
               ", httpOnly=" + httpOnly +
               ", sameSite='" + sameSite + '\'' +
               '}';
    }
}
