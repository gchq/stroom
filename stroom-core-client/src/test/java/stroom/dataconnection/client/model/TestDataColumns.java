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

package stroom.dataconnection.client.model;

import stroom.dataconnection.shared.DataColumn;
import stroom.dataconnection.shared.DataColumnType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDataColumns {

    private static final DataColumn MAIL = new DataColumn("mail", DataColumnType.TEXT);
    private static final DataColumn WHEN = new DataColumn("whenCreated", DataColumnType.DATE);

    @Test
    void same_noTypeIsText() {
        // A stored column with no type is shown as Text, so reading and writing back changes nothing
        assertThat(DataColumns.same(List.of(new DataColumn("mail", null)), List.of(MAIL)))
                .isTrue();
    }

    @Test
    void same_noneStored() {
        assertThat(DataColumns.same(null, List.of()))
                .isTrue();
        assertThat(DataColumns.same(null, List.of(MAIL)))
                .isFalse();
    }

    @Test
    void same_typeChanged() {
        assertThat(DataColumns.same(List.of(MAIL), List.of(new DataColumn("mail", DataColumnType.LONG))))
                .isFalse();
    }

    @Test
    void same_orderMatters() {
        assertThat(DataColumns.same(List.of(MAIL, WHEN), List.of(WHEN, MAIL)))
                .isFalse();
    }

    @Test
    void validateNewName_valid() {
        assertThat(DataColumns.validateNewName(" modifyTimestamp ", List.of("mail")))
                .isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    void validateNewName_blank(final String name) {
        assertThat(DataColumns.validateNewName(name, List.of()))
                .isEqualTo("An attribute name must be given");
    }

    @ParameterizedTest
    @ValueSource(strings = {"dn", "DN", " dn "})
    void validateNewName_dn(final String name) {
        // Every entry has a dn field of its own
        assertThat(DataColumns.validateNewName(name, List.of()))
                .isNotNull();
    }

    @Test
    void validateNewName_alreadyListedIgnoringCase() {
        // LDAP names ignore case
        assertThat(DataColumns.validateNewName("MAIL", List.of("mail")))
                .isEqualTo("'mail' is already listed");
    }

    @Test
    void names() {
        assertThat(DataColumns.names(List.of(MAIL, WHEN)))
                .containsExactly("mail", "whenCreated");
    }
}
