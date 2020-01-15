/*
 * Copyright 2019 Crown Copyright
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

package stroom.security.impl;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import stroom.util.shared.IsConfig;

import javax.inject.Singleton;

@Singleton
public class ContentSecurityConfig implements IsConfig {
    private String contentSecurityPolicy = "default-src 'self'; script-src 'self' 'unsafe-eval' 'unsafe-inline'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'self';";
    private String contentTypeOptions = "nosniff";
    private String frameOptions = "sameorigin";
    private String xssProtection = "1; mode=block";

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