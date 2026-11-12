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

package stroom.dataconnection.impl;

import stroom.query.api.DateTimeSettings;
import stroom.query.api.Param;
import stroom.query.api.TimeRange;

import com.unboundid.ldap.sdk.Filter;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestLdapFilterTemplate {

    private static final TimeRange TIME_RANGE = new TimeRange(null, "2026-11-01T00:00:00.000Z",
            "2026-11-10T12:30:00.000Z");
    private static final DateTimeSettings DATE_TIME_SETTINGS = DateTimeSettings.builder().build();

    private static Filter resolve(final String filter, final List<Param> params) {
        return LdapFilterTemplate.parse(filter).resolve(TIME_RANGE, params, DATE_TIME_SETTINGS);
    }

    @Test
    void placeholderTakesTheParameterValue() {
        final Filter filter = resolve("(&(objectClass=person)(department=:dept))",
                List.of(new Param("dept", "Operations")));

        assertThat(filter.toString()).isEqualTo("(&(objectClass=person)(department=Operations))");
    }

    @Test
    void parameterValueCannotChangeTheFilter() {
        // A value that would widen the search if pasted in unescaped.
        final Filter filter = resolve("(uid=:user)", List.of(new Param("user", "*)(uid=*")));

        assertThat(filter.getFilterType()).isEqualTo(Filter.FILTER_TYPE_EQUALITY);
        assertThat(filter.getAttributeName()).isEqualTo("uid");
        assertThat(filter.getAssertionValue()).isEqualTo("*)(uid=*");
        assertThat(filter.toString()).isEqualTo("(uid=\\2a\\29\\28uid=\\2a)");
    }

    @Test
    void timeRangeIsGeneralizedTime() {
        final LdapFilterTemplate template = LdapFilterTemplate.parse(
                "(&(modifyTimestamp>=:from)(modifyTimestamp<=:to))");

        assertThat(template.usesTimeRange()).isTrue();
        assertThat(template.resolve(TIME_RANGE, null, DATE_TIME_SETTINGS).toString())
                .isEqualTo("(&(modifyTimestamp>=20261101000000.000Z)(modifyTimestamp<=20261110123000.000Z))");
    }

    @Test
    void timeRangeTakesAFormat() {
        assertThat(resolve("(lastLogonTimestamp>=:from{epochMillis})", null).toString())
                .isEqualTo("(lastLogonTimestamp>=" + Instant.parse("2026-11-01T00:00:00Z").toEpochMilli() + ")");
    }

    @Test
    void placeholderMayFollowAWildcard() {
        assertThat(resolve("(cn=*:name*)", List.of(new Param("name", "smi(th")))
                .toString())
                .isEqualTo("(cn=*smi\\28th*)");
    }

    @Test
    void colonsElsewhereAreNotPlaceholders() {
        // An extensible match, and values holding colons.
        final LdapFilterTemplate template = LdapFilterTemplate.parse(
                "(&(cn:caseExactMatch:=Fred)(labeledURI=http://example.org)(description=at 12:30))");

        assertThat(template.placeholders()).isEmpty();
        assertThat(template.usesTimeRange()).isFalse();
        assertThat(template.resolve(null, null, DATE_TIME_SETTINGS).toString())
                .isEqualTo("(&(cn:caseExactMatch:=Fred)(labeledURI=http://example.org)(description=at 12:30))");
    }

    @Test
    void filterWithoutBracketsIsBracketed() {
        assertThat(resolve("objectClass=person", null).toString()).isEqualTo("(objectClass=person)");
    }

    @Test
    void blankFilterIsRefused() {
        assertThatThrownBy(() -> LdapFilterTemplate.parse("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(LdapFilterTemplate.NO_FILTER);
        assertThatThrownBy(() -> LdapFilterTemplate.parse(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(LdapFilterTemplate.NO_FILTER);
    }

    @Test
    void missingParameterIsReported() {
        assertThatThrownBy(() -> resolve("(uid=:user)", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Placeholder :user has no value");
    }

    @Test
    void unclosedFormatIsReported() {
        assertThatThrownBy(() -> LdapFilterTemplate.parse("(createTimestamp>=:from{epochMillis)"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no closing }");
    }

    @Test
    void invalidFilterIsReported() {
        assertThatThrownBy(() -> resolve("(&(cn=a)", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("The LDAP filter is not valid");
    }

    @Test
    void checkNeedsNoValues() {
        LdapFilterTemplate.parse("(uid=:user)").check();

        assertThatThrownBy(() -> LdapFilterTemplate.parse("(uid=:user").check())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("The LDAP filter is not valid");
    }
}
