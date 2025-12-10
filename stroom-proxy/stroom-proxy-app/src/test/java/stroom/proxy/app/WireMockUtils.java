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

package stroom.proxy.app;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.assertj.core.api.Assertions;

public class WireMockUtils {
    /**
     * Assert that a http header is present and has this value
     */
    public static void assertHeaderValue(final LoggedRequest loggedRequest,
                                         final String key,
                                         final String value) {
        Assertions.assertThat(loggedRequest.getHeader(key))
                .isNotNull()
                .isEqualTo(value);
    }
}
