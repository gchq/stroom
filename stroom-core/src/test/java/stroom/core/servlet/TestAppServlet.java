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

package stroom.core.servlet;

import stroom.ui.config.shared.UiConfig;
import stroom.ui.config.shared.UserPreferences;
import stroom.ui.config.shared.UserPreferencesService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestAppServlet {

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void testLanguageInRenderedPage(final boolean bootstrap) throws IOException {
        final UserPreferencesService preferences = mock(UserPreferencesService.class);
        when(preferences.fetchDefault()).thenReturn(UserPreferences.builder().build());
        final AppServlet servlet = new AppServlet(UiConfig::new, () -> preferences) {
            @Override
            String getScript() {
                return "ui/test/test.nocache.js";
            }

            @Override
            boolean useBootstrap() {
                return bootstrap;
            }
        };
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final StringWriter output = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(output));

        servlet.doGet(request, response);

        assertThat(output.toString())
                .containsPattern("<html\\s[^>]*lang=\"en\"")
                .contains("ui/test/test.nocache.js")
                .doesNotContain("@ROOT_CLASS@", "@BOOTSTRAP@");
    }
}
