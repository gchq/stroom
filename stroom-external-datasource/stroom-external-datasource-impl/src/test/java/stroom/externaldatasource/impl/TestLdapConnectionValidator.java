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

import stroom.externaldatasource.shared.LdapSettings;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestLdapConnectionValidator {

    private static final String URL = "ldaps://directory.example.org:636";

    static ExternalDataSourceConfig config(final List<String> allowedLdapUrlPatterns, final boolean allowPlainLdap) {
        return new ExternalDataSourceConfig(null, null, null, null, null, null, null, null, null, null, null, null,
                null, allowedLdapUrlPatterns, allowPlainLdap);
    }

    private static ExternalDataSourceConfig allowingAll() {
        return config(List.of("^ldaps?://.*$"), false);
    }

    private static LdapSettings.Builder settings(final String url) {
        return LdapSettings.builder()
                .url(url)
                .credentialName("directory-reader")
                .baseDn("ou=People,dc=example,dc=org");
    }

    private static List<String> problems(final LdapSettings settings, final ExternalDataSourceConfig config) {
        return LdapConnectionValidator.findProblems(settings, config);
    }

    @Test
    void allowedSettingsHaveNoProblems() {
        assertThat(problems(settings(URL).build(), allowingAll())).isEmpty();
        assertThat(problems(settings("ldap://directory.example.org:389").startTls(true).build(), allowingAll()))
                .isEmpty();
    }

    @Test
    void noLdapUrlIsAllowedByDefault() {
        assertThat(problems(settings(URL).build(), new ExternalDataSourceConfig()))
                .singleElement()
                .asString()
                .contains("does not match any pattern in the externalDataSource.allowedLdapUrlPatterns property");
    }

    @Test
    void missingUrlIsReported() {
        assertThat(problems(null, allowingAll())).containsExactly("No LDAP URL has been set.");
        assertThat(problems(settings(" ").build(), allowingAll())).containsExactly("No LDAP URL has been set.");
    }

    @Test
    void urlMustBeLdap() {
        assertThat(problems(settings("jdbc:mysql://db:3306/x").build(), config(List.of(".*"), false)))
                .anySatisfy(problem -> assertThat(problem).contains("is not an LDAP URL"));
        assertThat(problems(settings("ldapi://%2fvar%2frun%2fldapi").build(), config(List.of(".*"), true)))
                .anySatisfy(problem -> assertThat(problem).isEqualTo("The LDAP URL must start ldaps:// or ldap://."));
    }

    @Test
    void plainLdapNeedsTheAdministrator() {
        final LdapSettings plain = settings("ldap://directory.example.org:389").build();

        assertThat(problems(plain, allowingAll()))
                .singleElement()
                .asString()
                .contains("externalDataSource.allowPlainLdap");
        assertThat(problems(plain, config(List.of("^ldaps?://.*$"), true))).isEmpty();
    }

    @Test
    void startTlsIsOnlyForLdapUrls() {
        assertThat(problems(settings(URL).startTls(true).build(), allowingAll()))
                .containsExactly("StartTLS is only for ldap:// URLs. An ldaps:// connection is already encrypted.");
    }

    @Test
    void urlMayNotCarryASearch() {
        assertThat(problems(settings(URL + "/dc=example,dc=org??sub?(objectClass=*)").build(),
                config(List.of(".*"), false)))
                .anySatisfy(problem -> assertThat(problem).startsWith("The LDAP URL may only give the scheme"));
    }

    @Test
    void urlMustNameAHost() {
        assertThat(problems(settings("ldaps://").build(), config(List.of(".*"), false)))
                .anySatisfy(problem -> assertThat(problem).contains("must name the directory's host"));
    }

    @Test
    void badAllowedPatternIsReported() {
        assertThat(problems(settings(URL).build(), config(List.of("(unclosed"), false)))
                .anySatisfy(problem -> assertThat(problem).contains("is not a valid regular expression"));
    }

    @Test
    void baseDnMustBeAValidDn() {
        assertThat(problems(settings(URL).baseDn(null).build(), allowingAll()))
                .containsExactly("No base DN has been set, such as ou=People,dc=example,dc=org.");
        assertThat(problems(settings(URL).baseDn("not a dn").build(), allowingAll()))
                .containsExactly("'not a dn' is not a valid DN, such as ou=People,dc=example,dc=org.");
    }

    @Test
    void pageSizeMustBeInRange() {
        assertThat(problems(settings(URL).pageSize(0).build(), allowingAll()))
                .containsExactly("The page size must be from 1 to " + LdapSettings.MAX_PAGE_SIZE + ".");
        assertThat(problems(settings(URL).pageSize(LdapSettings.MAX_PAGE_SIZE + 1).build(), allowingAll()))
                .hasSize(1);
        assertThat(problems(settings(URL).pageSize(LdapSettings.MAX_PAGE_SIZE).build(), allowingAll())).isEmpty();
    }

    @Test
    void credentialIsRequired() {
        assertThat(problems(settings(URL).credentialName(null).build(), allowingAll()))
                .singleElement()
                .asString()
                .contains("Anonymous binds are not allowed");
    }

    @Test
    void validateListsEveryProblem() {
        assertThatThrownBy(() -> LdapConnectionValidator.validate(
                settings("ldap://directory.example.org:389").credentialName(null).build(),
                allowingAll()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowPlainLdap")
                .hasMessageContaining("Anonymous binds are not allowed");
    }
}
