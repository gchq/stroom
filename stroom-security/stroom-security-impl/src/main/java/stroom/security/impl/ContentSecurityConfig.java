/*
 * Copyright 2019 Crown Copyright
 *
 *
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Licensed under the Apache License, Version 2.0 (the "License");
 * See the License for the specific language governing permissions and
 * Unless required by applicable law or agreed to in writing, software
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * You may obtain a copy of the License at
 * distributed under the License is distributed on an "AS IS" BASIS,
 * import stroom.util.shared.IsStroomConfig;
 * limitations under the License.
 * you may not use this file except in compliance with the License.
 */

package stroom.security.impl;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import javax.inject.Singleton;

@Singleton
public class ContentSecurityConfig extends AbstractConfig implements IsStroomConfig {

    public static final String PROP_NAME_CONTENT_SECURITY_POLICY = "contentSecurityPolicy";

    private String contentSecurityPolicy = "" +
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-eval' 'unsafe-inline'; " +
            "img-src 'self' data:; " +
            "style-src 'self' 'unsafe-inline'; " +
            "frame-ancestors 'self';";
    private String contentTypeOptions = "nosniff";
    private String frameOptions = "sameorigin";
    private String xssProtection = "1; mode=block";

    @JsonProperty(PROP_NAME_CONTENT_SECURITY_POLICY)
    @JsonPropertyDescription("The content security policy")
    public String getContentSecurityPolicy() {
        return contentSecurityPolicy;
    }

    public void setContentSecurityPolicy(final String contentSecurityPolicy) {
        this.contentSecurityPolicy = contentSecurityPolicy;
    }

    @JsonPropertyDescription("The content type options")
    public String getContentTypeOptions() {
        return contentTypeOptions;
    }

    public void setContentTypeOptions(final String contentTypeOptions) {
        this.contentTypeOptions = contentTypeOptions;
    }

    @JsonPropertyDescription("The frame options")
    public String getFrameOptions() {
        return frameOptions;
    }

    public void setFrameOptions(final String frameOptions) {
        this.frameOptions = frameOptions;
    }

    @JsonPropertyDescription("XSS protection")
    public String getXssProtection() {
        return xssProtection;
    }

    public void setXssProtection(final String xssProtection) {
        this.xssProtection = xssProtection;
    }
}
