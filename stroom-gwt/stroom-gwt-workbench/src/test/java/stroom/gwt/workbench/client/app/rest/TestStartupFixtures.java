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


package stroom.gwt.workbench.client.app.rest;

import stroom.security.shared.AppPermission;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestStartupFixtures {

    @Test
    void testDefaults() {
        final FixtureSession session = StartupFixtures.defaults().newSession();

        for (final String path : new String[]{
                StartupFixtures.SESSION_INFO_PATH,
                StartupFixtures.EXTENDED_UI_CONFIG_PATH,
                StartupFixtures.USER_PREFERENCES_PATH,
                StartupFixtures.APP_PERMISSIONS_PATH,
                StartupFixtures.DOCUMENT_TYPES_PATH}) {
            final FixtureSession.Exchange exchange = session.exchange(new RecordedRequest("GET", path, null, null));
            assertThat(exchange.isHandled()).as(path).isTrue();
            // Every reply is valid JSON
            assertThat(JsonValues.parse(exchange.getReply().getBody())).as(path).isInstanceOf(Map.class);
        }
        assertThat(session.exchange(new RecordedRequest(
                "POST", StartupFixtures.CHECK_DOCUMENT_PERMISSION_PATH, null, "{}")).getReply().getBody())
                .isEqualTo("true");
    }

    @Test
    void testDefaultDocumentTypes() {
        final Map<?, ?> documentTypes = (Map<?, ?>) JsonValues.parse(body(StartupFixtures.defaults(),
                StartupFixtures.DOCUMENT_TYPES_PATH));
        final List<Object> types = new ArrayList<>((List<?>) documentTypes.get("types"));

        assertThat(types).hasSize(27);
        assertThat(types.get(0)).isEqualTo(Map.of(
                "group", "STRUCTURE", "type", "Folder", "displayType", "Folder", "icon", "FOLDER"));
        assertThat(types).contains(Map.of("group", "CONFIGURATION", "type", "Dictionary",
                "displayType", "Dictionary", "icon", "DOCUMENT_DICTIONARY"));
        // All the types are visible, as in the corpus
        assertThat(documentTypes.get("visibleTypes")).isEqualTo(types);
    }

    @Test
    void testDefaultUiConfig() {
        final Map<?, ?> extended = (Map<?, ?>) JsonValues.parse(body(StartupFixtures.defaults(),
                StartupFixtures.EXTENDED_UI_CONFIG_PATH));
        final Map<?, ?> uiConfig = (Map<?, ?>) extended.get("uiConfig");

        assertThat(uiConfig.get("namePattern")).isEqualTo("^[a-zA-Z0-9_\\- \\.\\(\\)]{1,}$");
        assertThat((String) uiConfig.get("welcomeHtml")).contains("About Stroom");
    }

    @Test
    void testCustomised() {
        final UserRef alice = new UserRef("u-1", "alice@corp", "Alice Anderson", "Alice B. Anderson", false, true);
        final StartupFixtures.Builder builder = StartupFixtures.builder()
                .user(alice)
                .nodeName("node2")
                .buildVersion("v7.13")
                .appPermissions(AppPermission.MANAGE_USERS_PERMISSION, AppPermission.MANAGE_TASKS_PERMISSION)
                .uiConfig("{\"welcomeHtml\":\"<h1>Hi</h1>\"}")
                .userPreferences("{\"theme\":\"Dark\"}")
                .documentPermission(false);
        final RestFixtures fixtures = builder.build();

        assertThat(builder.getUser()).isEqualTo(alice);
        assertThat(builder.getAppPermissions())
                .containsExactly(AppPermission.MANAGE_USERS_PERMISSION, AppPermission.MANAGE_TASKS_PERMISSION);
        assertThat(JsonValues.jsonEquals(body(fixtures, StartupFixtures.SESSION_INFO_PATH),
                "{\"userRef\":{\"uuid\":\"u-1\",\"subjectId\":\"alice@corp\",\"displayName\":\"Alice Anderson\","
                        + "\"fullName\":\"Alice B. Anderson\",\"group\":false,\"enabled\":true},"
                        + "\"nodeName\":\"node2\",\"buildInfo\":{\"upTime\":1710000000000,"
                        + "\"buildVersion\":\"v7.13\",\"buildTime\":1700000000000}}")).isTrue();
        assertThat(body(fixtures, StartupFixtures.APP_PERMISSIONS_PATH))
                .contains("\"permissions\":[\"MANAGE_USERS_PERMISSION\",\"MANAGE_TASKS_PERMISSION\"]")
                .contains("alice@corp");
        assertThat(body(fixtures, StartupFixtures.EXTENDED_UI_CONFIG_PATH))
                .startsWith("{\"uiConfig\":{\"welcomeHtml\":\"<h1>Hi</h1>\"}");
        assertThat(body(fixtures, StartupFixtures.USER_PREFERENCES_PATH)).isEqualTo("{\"theme\":\"Dark\"}");
        assertThat(fixtures.newSession().exchange(new RecordedRequest(
                "POST", StartupFixtures.CHECK_DOCUMENT_PERMISSION_PATH, null, "{}")).getReply().getBody())
                .isEqualTo("false");
    }

    @Test
    void testWholeReplacements() {
        final RestFixtures fixtures = StartupFixtures.builder()
                .sessionInfo("{\"nodeName\":\"x\"}")
                .extendedUiConfig("{\"uiConfig\":{}}")
                .build();

        assertThat(body(fixtures, StartupFixtures.SESSION_INFO_PATH)).isEqualTo("{\"nodeName\":\"x\"}");
        assertThat(body(fixtures, StartupFixtures.EXTENDED_UI_CONFIG_PATH)).isEqualTo("{\"uiConfig\":{}}");
    }

    private static String body(final RestFixtures fixtures, final String path) {
        return fixtures.newSession().exchange(new RecordedRequest("GET", path, null, null)).getReply().getBody();
    }
}
