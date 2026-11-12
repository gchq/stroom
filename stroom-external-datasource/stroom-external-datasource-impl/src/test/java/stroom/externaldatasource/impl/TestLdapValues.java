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

package stroom.externaldatasource.impl;

import stroom.externaldatasource.shared.DataColumnType;
import stroom.query.language.functions.ValBoolean;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;

import com.unboundid.ldap.sdk.Attribute;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestLdapValues {

    private static final byte[] GUID = {(byte) 0x8f, 0x00, 0x12, (byte) 0xff, 0x01};

    @Test
    void missingAttributeIsEmpty() {
        assertThat(LdapValues.read(null, DataColumnType.TEXT)).isEqualTo(ValNull.INSTANCE);
        assertThat(LdapValues.read(new Attribute("mail"), DataColumnType.TEXT)).isEqualTo(ValNull.INSTANCE);
    }

    @Test
    void severalValuesAreJoinedAsText() {
        final Attribute memberOf = new Attribute("memberOf", "cn=Ops,dc=example,dc=org", "cn=Admins,dc=example,dc=org");

        assertThat(LdapValues.read(memberOf, DataColumnType.TEXT))
                .isEqualTo(ValString.create("cn=Ops,dc=example,dc=org; cn=Admins,dc=example,dc=org"));
        assertThat(LdapValues.read(memberOf, null))
                .as("no type means text")
                .isEqualTo(ValString.create("cn=Ops,dc=example,dc=org; cn=Admins,dc=example,dc=org"));
    }

    @Test
    void severalValuesAreNotOfATypedColumn() {
        assertThatThrownBy(() -> LdapValues.read(new Attribute("uidNumber", "1", "2"), DataColumnType.LONG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has 2 values");
    }

    @Test
    void numbersAndBooleansAreReadAsForCsv() {
        assertThat(LdapValues.read(new Attribute("uidNumber", "1001"), DataColumnType.LONG))
                .isEqualTo(ValLong.create(1001));
        assertThat(LdapValues.read(new Attribute("enabled", "TRUE"), DataColumnType.BOOLEAN))
                .isEqualTo(ValBoolean.create(true));
        assertThatThrownBy(() -> LdapValues.read(new Attribute("uidNumber", "x"), DataColumnType.LONG))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void generalizedTimeIsADate() {
        assertThat(LdapValues.read(new Attribute("modifyTimestamp", "20261112093000Z"), DataColumnType.DATE))
                .isEqualTo(ValDate.create(Instant.parse("2026-11-12T09:30:00Z").toEpochMilli()));
        assertThat(LdapValues.read(new Attribute("whenCreated", "20261112093000.5+0100"), DataColumnType.DATE))
                .isEqualTo(ValDate.create(Instant.parse("2026-11-12T08:30:00.500Z").toEpochMilli()));
    }

    @Test
    void activeDirectoryFiletimeIsADate() {
        // 2026-11-12T09:30:00Z as 100 nanosecond intervals since 1601.
        final long ticks = (Instant.parse("2026-11-12T09:30:00Z").toEpochMilli() + 11_644_473_600_000L) * 10_000L;

        assertThat(LdapValues.read(new Attribute("lastLogonTimestamp", Long.toString(ticks)), DataColumnType.DATE))
                .isEqualTo(ValDate.create(Instant.parse("2026-11-12T09:30:00Z").toEpochMilli()));
        assertThat(LdapValues.read(new Attribute("accountExpires", "0"), DataColumnType.DATE))
                .as("never")
                .isEqualTo(ValNull.INSTANCE);
        assertThat(LdapValues.read(new Attribute("accountExpires", Long.toString(Long.MAX_VALUE)),
                DataColumnType.DATE))
                .as("no expiry")
                .isEqualTo(ValNull.INSTANCE);
    }

    @Test
    void isoDatesAreReadToo() {
        assertThat(LdapValues.read(new Attribute("hired", "2026-11-12"), DataColumnType.DATE))
                .isEqualTo(ValDate.create(Instant.parse("2026-11-12T00:00:00Z").toEpochMilli()));
        assertThatThrownBy(() -> LdapValues.read(new Attribute("hired", "soon"), DataColumnType.DATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void binaryValuesAreBase64Text() {
        final Attribute guid = new Attribute("objectGUID", GUID);

        assertThat(LdapValues.isBinary(guid)).isTrue();
        assertThat(LdapValues.read(guid, DataColumnType.TEXT))
                .isEqualTo(ValString.create(Base64.getEncoder().encodeToString(GUID)));
        assertThatThrownBy(() -> LdapValues.read(guid, DataColumnType.LONG))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("can only be read as text");
    }

    @Test
    void binaryOptionMakesAnAttributeBinary() {
        assertThat(LdapValues.isBinary(new Attribute("userCertificate;binary", "text"))).isTrue();
        assertThat(LdapValues.isBinary(new Attribute("cn", "Zoë Smith"))).isFalse();
        assertThat(LdapValues.isBinary(new Attribute("description", "line one\nline two"))).isFalse();
    }

    @Test
    void typesAreGuessedFromTheValues() {
        assertThat(LdapValues.guessType(List.of(
                new Attribute("modifyTimestamp", "20261112093000Z"),
                new Attribute("modifyTimestamp", "20250101000000Z"))))
                .isEqualTo(DataColumnType.DATE);
        assertThat(LdapValues.guessType(List.of(new Attribute("uidNumber", "1001"), new Attribute("uidNumber", "7"))))
                .isEqualTo(DataColumnType.LONG);
        assertThat(LdapValues.guessType(List.of(new Attribute("cn", "Smith"))))
                .isEqualTo(DataColumnType.TEXT);
        assertThat(LdapValues.guessType(List.of(new Attribute("uidNumber", "1", "2"))))
                .as("several values can only be text")
                .isEqualTo(DataColumnType.TEXT);
        assertThat(LdapValues.guessType(List.of(new Attribute("employeeNumber", "00123"))))
                .as("leading zeros are kept as text")
                .isEqualTo(DataColumnType.TEXT);
    }
}
