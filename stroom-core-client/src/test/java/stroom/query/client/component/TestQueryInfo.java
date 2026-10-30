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

package stroom.query.client.component;

import stroom.activity.shared.Activity;
import stroom.activity.shared.Activity.ActivityDetails;
import stroom.activity.shared.Activity.Prop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestQueryInfo {

    @Test
    void testNoActivity() {
        assertThat(QueryInfo.isRequired(null)).isTrue();
    }

    @Test
    void testNoProperty() {
        assertThat(QueryInfo.isRequired(activity(List.of()))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "FALSE", "False"})
    void testNotRequired(final String value) {
        assertThat(QueryInfo.isRequired(activity(List.of(prop(value))))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "", "no"})
    void testRequired(final String value) {
        assertThat(QueryInfo.isRequired(activity(List.of(prop(value))))).isTrue();
    }

    private static Prop prop(final String value) {
        return new Prop("requireQueryInfo", "requireQueryInfo", null, null, value, null, null);
    }

    private static Activity activity(final List<Prop> props) {
        return Activity.builder().details(new ActivityDetails(props)).build();
    }
}
