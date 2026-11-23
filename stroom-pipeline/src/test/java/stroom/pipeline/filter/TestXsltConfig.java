/*
 * Copyright 2026 Crown Copyright
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

package stroom.pipeline.filter;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class TestXsltConfig {

    @Test
    void testDefaultsAreSecure() {
        final XsltConfig xsltConfig = new XsltConfig();
        assertThat(xsltConfig.isSecureMode()).isTrue();
        assertThat(xsltConfig.getAllowedUriPrefixes()).isEmpty();
        assertThat(xsltConfig.getAllowedEmailRecipients()).isEmpty();
        assertThat(xsltConfig.getMaxEmailsPerRun()).isEqualTo(XsltConfig.DEFAULT_MAX_EMAILS_PER_RUN);
    }

    @Test
    void testMissingValuesAreSecure() {
        final XsltConfig xsltConfig = new XsltConfig(null, null, null, null, null, null);
        assertThat(xsltConfig.isSecureMode()).isTrue();
        assertThat(xsltConfig.getAllowedUriPrefixes()).isEmpty();
        assertThat(xsltConfig.getAllowedEmailRecipients()).isEmpty();
        assertThat(xsltConfig.getMaxEmailsPerRun()).isEqualTo(XsltConfig.DEFAULT_MAX_EMAILS_PER_RUN);
    }

    @Test
    void testNullPrefixesLeftOut() {
        // E.g. an empty "- " item in the YAML list.
        final XsltConfig xsltConfig = new XsltConfig(null,
                null,
                true,
                Arrays.asList(null, "/data/lookups", null),
                Arrays.asList(null, "@example.com"),
                null);
        assertThat(xsltConfig.getAllowedUriPrefixes()).containsExactly("/data/lookups");
        assertThat(xsltConfig.getAllowedEmailRecipients()).containsExactly("@example.com");
    }
}
