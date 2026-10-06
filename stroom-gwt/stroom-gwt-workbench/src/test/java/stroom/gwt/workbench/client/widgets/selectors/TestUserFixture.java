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


package stroom.gwt.workbench.client.widgets.selectors;

import stroom.gwt.workbench.client.app.rest.JsonValues;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestUserFixture {

    @Test
    void testNoFilterFindsEveryone() {
        final Map<?, ?> result = find("{\"expression\":{\"op\":\"AND\"}}");
        assertThat(names(result)).containsExactly(
                "Alice Anderson", "Bob Brown", "Carol Clark", "Administrators", "Analysts");
        final Map<?, ?> page = (Map<?, ?>) result.get("pageResponse");
        assertThat(page.get("total").toString()).isEqualTo("5");
        assertThat(page.get("exact")).isEqualTo(true);
    }

    @Test
    void testFilterMatchesDisplayNameOrSubjectIdIgnoringCase() {
        final String request = "{\"expression\":{\"op\":\"AND\",\"children\":[{\"op\":\"OR\",\"children\":["
                               + "{\"field\":\"Display Name\",\"condition\":\"CONTAINS\",\"value\":\" AN \"}]}]}}";
        assertThat(names(find(request))).containsExactly("Alice Anderson", "Analysts");
        assertThat(names(find(request.replace(" AN ", "bob")))).containsExactly("Bob Brown");
    }

    @Test
    void testBlankFilterIsIgnored() {
        final String request = "{\"expression\":{\"children\":[{\"field\":\"x\",\"value\":\"  \"}]}}";
        assertThat(names(find(request))).hasSize(5);
    }

    @Test
    void testUserFields() {
        final Map<?, ?> result = find("{}");
        final Map<?, ?> carol = (Map<?, ?>) ((List<?>) result.get("values")).get(2);
        assertThat(carol.get("uuid")).isEqualTo("u-carol");
        assertThat(carol.get("subjectId")).isEqualTo("carol");
        assertThat(carol.get("fullName")).isEqualTo("Carol Clark");
        assertThat(carol.get("group")).isEqualTo(false);
        assertThat(carol.get("enabled")).isEqualTo(false);
        final Map<?, ?> admins = (Map<?, ?>) ((List<?>) result.get("values")).get(3);
        assertThat(admins.containsKey("fullName")).isTrue();
        assertThat(admins.get("fullName")).isNull();
        assertThat(admins.get("group")).isEqualTo(true);
    }

    @Test
    void testGet() {
        assertThat(UserFixture.fixtureUsers().get("u-bob").getDisplayName()).isEqualTo("Bob Brown");
        assertThatThrownBy(() -> UserFixture.fixtureUsers().get("nobody"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Map<?, ?> find(final String request) {
        return (Map<?, ?>) JsonValues.parse(UserFixture.fixtureUsers().find(request));
    }

    private static List<String> names(final Map<?, ?> result) {
        final List<String> names = new ArrayList<>();
        for (final Object user : (List<?>) result.get("values")) {
            names.add((String) ((Map<?, ?>) user).get("displayName"));
        }
        return names;
    }
}
