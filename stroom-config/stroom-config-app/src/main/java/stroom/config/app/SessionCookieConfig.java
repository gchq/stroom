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
import org.eclipse.jetty.http.HttpCookie.SameSite;

import java.util.Objects;


@JsonPropertyOrder(alphabetic = true)
public class SessionCookieConfig extends AbstractConfig implements IsStroomConfig {

    private static final boolean DEFAULT_SECURE = true;
    private static final boolean DEFAULT_HTTP_ONLY = true;
    private static final SameSite DEFAULT_SAME_SITE = SameSite.STRICT;

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
        secure = DEFAULT_SECURE;
        httpOnly = DEFAULT_HTTP_ONLY;
        sameSite = DEFAULT_SAME_SITE;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SessionCookieConfig(@JsonProperty("secure") final Boolean secure,
                               @JsonProperty("httpOnly") final Boolean httpOnly,
                               @JsonProperty("sameSite") final SameSite sameSite) {
        this.secure = Objects.requireNonNullElse(secure, DEFAULT_SECURE);
        this.httpOnly = Objects.requireNonNullElse(httpOnly, DEFAULT_HTTP_ONLY);
        this.sameSite = Objects.requireNonNullElse(sameSite, DEFAULT_SAME_SITE);
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
