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

package stroom.security.common.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class ContentSecurityConfig extends AbstractConfig implements IsStroomConfig, IsProxyConfig {

    public static final String PROP_NAME_CONTENT_SECURITY_POLICY = "contentSecurityPolicy";
    public static final String PROP_NAME_STRICT_TRANSPORT_SECURITY = "strictTransportSecurity";

    private final String contentSecurityPolicy;
    private final String contentTypeOptions;
    private final String frameOptions;
    private final String xssProtection;
    private final String strictTransportSecurity;

    public ContentSecurityConfig() {
        contentSecurityPolicy = "" +
                "default-src 'self'; " +
                "script-src 'self' 'unsafe-eval' 'unsafe-inline'; " +
                "img-src 'self' data:; " +
                "style-src 'self' 'unsafe-inline'; " +
                "frame-ancestors 'self';";
        contentTypeOptions = "nosniff";
        frameOptions = "sameorigin";
        xssProtection = "1; mode=block";
        strictTransportSecurity = "max-age=31536000; includeSubDomains; preload";
    }

    @JsonCreator
    public ContentSecurityConfig(
            @JsonProperty(PROP_NAME_CONTENT_SECURITY_POLICY) final String contentSecurityPolicy,
            @JsonProperty("contentTypeOptions") final String contentTypeOptions,
            @JsonProperty("frameOptions") final String frameOptions,
            @JsonProperty("xssProtection") final String xssProtection,
            @JsonProperty(PROP_NAME_STRICT_TRANSPORT_SECURITY) final String strictTransportSecurity) {

        this.contentSecurityPolicy = contentSecurityPolicy;
        this.contentTypeOptions = contentTypeOptions;
        this.frameOptions = frameOptions;
        this.xssProtection = xssProtection;
        this.strictTransportSecurity = strictTransportSecurity;
    }

    @JsonProperty(PROP_NAME_CONTENT_SECURITY_POLICY)
    @JsonPropertyDescription("The content security policy (HTTP header 'Content-Security-Policy')")
    public String getContentSecurityPolicy() {
        return contentSecurityPolicy;
    }

    @JsonPropertyDescription("The content type options (HTTP header 'X-Content-Type-Options')")
    public String getContentTypeOptions() {
        return contentTypeOptions;
    }

    @JsonPropertyDescription("The frame options (HTTP header 'X-Frame-Options')")
    public String getFrameOptions() {
        return frameOptions;
    }

    @JsonPropertyDescription("XSS protection (HTTP header 'X-XSS-Protection')")
    public String getXssProtection() {
        return xssProtection;
    }

    @JsonProperty(PROP_NAME_STRICT_TRANSPORT_SECURITY)
    @JsonPropertyDescription("HTTP Strict Transport Security (HSTS) (HTTP header 'Strict-Transport-Security'). " +
            "If this is set then the first HTTPS request will make the browser use HTTPS for all future HTTP " +
            "requests. This browser state cannot be cleared just by clearing the browser cache.")
    public String getStrictTransportSecurity() {
        return strictTransportSecurity;
    }

    public ContentSecurityConfig withContentSecurityPolicy(final String contentSecurityPolicy) {
        return new ContentSecurityConfig(
                contentSecurityPolicy,
                contentTypeOptions,
                frameOptions,
                xssProtection,
                strictTransportSecurity);
    }

    public ContentSecurityConfig withStrictTransportSecurity(final String strictTransportSecurity) {
        return new ContentSecurityConfig(
                contentSecurityPolicy,
                contentTypeOptions,
                frameOptions,
                xssProtection,
                strictTransportSecurity);
    }

    @Override
    public String toString() {
        return "ContentSecurityConfig{" +
                "contentSecurityPolicy='" + contentSecurityPolicy + '\'' +
                ", contentTypeOptions='" + contentTypeOptions + '\'' +
                ", frameOptions='" + frameOptions + '\'' +
                ", xssProtection='" + xssProtection + '\'' +
                ", strictTransportSecurity='" + strictTransportSecurity + '\'' +
                '}';
    }
}
