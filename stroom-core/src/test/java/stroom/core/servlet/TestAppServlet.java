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
import stroom.ui.config.shared.UserPreferencesService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestAppServlet {

    @Test
    void pageDeclaresItsLanguage() throws Exception {
        // The page Stroom's UI loads in says it is in English, for screen readers (gchq/stroom#5408).
        // This page checks the user is signed in before it loads the UI's script.
        final String html = render(new StroomServlet(UiConfig::new, () -> mock(UserPreferencesService.class)));

        assertThat(html)
                .contains("<html lang=\"en\" class=\"stroom")
                .contains("s.src = 'ui/stroom/stroom.nocache.js';")
                .doesNotContain("@ROOT_CLASS@", "@TITLE@", "@ON_CONTEXT_MENU@", "@BOOTSTRAP@");
    }

    @Test
    void signInPageDeclaresItsLanguage() throws Exception {
        // This page loads its script straight away, without checking the user is signed in.
        final String html = render(new SignInServlet(UiConfig::new, () -> mock(UserPreferencesService.class)));

        assertThat(html)
                .contains("<html lang=\"en\" class=\"stroom")
                .contains("<script type=\"text/javascript\" src='ui/stroom/stroom.nocache.js'></script>")
                .doesNotContain("@ROOT_CLASS@", "@TITLE@", "@ON_CONTEXT_MENU@", "@BOOTSTRAP@");
    }

    private String render(final AppServlet servlet) throws Exception {
        final HttpServletRequest request = mock(HttpServletRequest.class);
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final StringWriter page = new StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(page));

        servlet.doGet(request, response);

        return page.toString();
    }
}
