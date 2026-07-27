/*
 * Copyright 2016-2026 Crown Copyright
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

import org.eclipse.jetty.http.HttpCookie.SameSite;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestSessionCookieConfig {

    @Test
    void omittedSameSiteDefaultsToStrict() {
        // A partial sessionCookie config block (e.g. only 'secure' set, sameSite absent) must still get the
        // CSRF-protective Strict default rather than a null that drops the SameSite attribute entirely.
        final SessionCookieConfig config = new SessionCookieConfig(false, null, null);
        assertThat(config.getSameSite()).isEqualTo(SameSite.STRICT);
    }

    @Test
    void defaultConfigIsStrict() {
        assertThat(new SessionCookieConfig().getSameSite()).isEqualTo(SameSite.STRICT);
    }
}
