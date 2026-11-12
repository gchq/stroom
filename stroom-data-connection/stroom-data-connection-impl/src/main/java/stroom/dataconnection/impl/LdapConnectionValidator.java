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

import stroom.dataconnection.shared.LdapSettings;
import stroom.util.shared.NullSafe;

import com.unboundid.ldap.sdk.DN;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.LDAPURL;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// Checks the LDAP settings of a Data Connection against the administrator's controls in
/// [DataConnectionConfig].
///
/// The administrator's controls are:
///
/// - The URL must match one of the `allowedLdapUrlPatterns` patterns. With none, no LDAP connection
///   is allowed.
/// - The connection must be encrypted, with `ldaps://` or StartTLS, unless `allowPlainLdap` is on.
/// - A document must name a credential, so its Use permission controls who may search the directory.
///   Anonymous binds are not allowed.
///
/// The URL may give only the scheme, host and port: a base DN, attributes, scope or filter in it would
/// be ignored, so it is refused rather than mislead. It is used when a document is saved, when its
/// connection is tested and before each query.
public final class LdapConnectionValidator {

    static final String LDAP_SCHEME = "ldap";
    static final String LDAPS_SCHEME = "ldaps";

    private LdapConnectionValidator() {
        // Static methods only.
    }

    /// Finds every way the settings break the administrator's controls.
    ///
    /// @param settings The LDAP settings to check; `null` is reported as a missing URL.
    /// @param config   The administrator's controls.
    /// @return A message per problem, or an empty list if the settings may be used.
    public static List<String> findProblems(final LdapSettings settings, final DataConnectionConfig config) {
        final List<String> problems = new ArrayList<>();
        final String url = NullSafe.get(settings, LdapSettings::getUrl);
        if (NullSafe.isBlankString(url)) {
            problems.add("No LDAP URL has been set.");
            return problems;
        }
        final String trimmedUrl = url.trim();
        final LDAPURL ldapUrl = parse(trimmedUrl, problems);
        if (ldapUrl != null) {
            checkUrl(ldapUrl, settings, config, problems);
        }
        if (!isAllowed(trimmedUrl, config.getAllowedLdapUrlPatterns(), problems)) {
            problems.add("The LDAP URL '" + trimmedUrl + "' does not match any pattern in the "
                         + "dataConnection.allowedLdapUrlPatterns property. Ask an administrator to allow it.");
        }
        checkBaseDn(settings.getBaseDn(), problems);
        final Integer pageSize = settings.getPageSize();
        if (pageSize != null && (pageSize < 1 || pageSize > LdapSettings.MAX_PAGE_SIZE)) {
            problems.add("The page size must be from 1 to " + LdapSettings.MAX_PAGE_SIZE + ".");
        }
        if (NullSafe.isBlankString(settings.getCredentialName())) {
            problems.add("Choose a username and password credential holding the bind DN and its password. "
                         + "Anonymous binds are not allowed.");
        }
        return problems;
    }

    /// Checks the settings and throws if they break the administrator's controls.
    ///
    /// @param settings The LDAP settings to check.
    /// @param config   The administrator's controls.
    /// @throws IllegalArgumentException If there is any problem; its message lists them all.
    public static void validate(final LdapSettings settings, final DataConnectionConfig config) {
        final List<String> problems = findProblems(settings, config);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", problems));
        }
    }

    private static LDAPURL parse(final String url, final List<String> problems) {
        try {
            return new LDAPURL(url);
        } catch (final LDAPException e) {
            problems.add("'" + url + "' is not an LDAP URL, such as ldaps://directory.example.org:636. "
                         + e.getMessage());
            return null;
        }
    }

    private static void checkUrl(final LDAPURL ldapUrl,
                                 final LdapSettings settings,
                                 final DataConnectionConfig config,
                                 final List<String> problems) {
        final String scheme = ldapUrl.getScheme();
        final boolean startTls = NullSafe.isTrue(settings.getStartTls());
        if (!LDAP_SCHEME.equals(scheme) && !LDAPS_SCHEME.equals(scheme)) {
            problems.add("The LDAP URL must start ldaps:// or ldap://.");
        } else if (LDAPS_SCHEME.equals(scheme) && startTls) {
            problems.add("StartTLS is only for ldap:// URLs. An ldaps:// connection is already encrypted.");
        } else if (LDAP_SCHEME.equals(scheme) && !startTls && !config.isAllowPlainLdap()) {
            problems.add("An ldap:// connection must use StartTLS, or the bind password and results are sent "
                         + "unencrypted. Use ldaps://, or turn on StartTLS. Plain LDAP can only be used if an "
                         + "administrator turns on dataConnection.allowPlainLdap.");
        }
        if (!ldapUrl.hostProvided()) {
            problems.add("The LDAP URL must name the directory's host, as in ldaps://directory.example.org:636.");
        }
        if (ldapUrl.baseDNProvided()
            || ldapUrl.attributesProvided()
            || ldapUrl.scopeProvided()
            || ldapUrl.filterProvided()) {
            problems.add("The LDAP URL may only give the scheme, host and port. Set the base DN and scope in "
                         + "their own settings, the attributes in the attributes table, and the filter in each "
                         + "query.");
        }
    }

    private static void checkBaseDn(final String baseDn, final List<String> problems) {
        if (NullSafe.isBlankString(baseDn)) {
            problems.add("No base DN has been set, such as ou=People,dc=example,dc=org.");
        } else if (!DN.isValidDN(baseDn.trim())) {
            problems.add("'" + baseDn.trim() + "' is not a valid DN, such as ou=People,dc=example,dc=org.");
        }
    }

    private static boolean isAllowed(final String value,
                                     final List<String> allowedPatterns,
                                     final List<String> problems) {
        for (final String allowedPattern : NullSafe.list(allowedPatterns)) {
            if (NullSafe.isNonBlankString(allowedPattern)) {
                try {
                    if (Pattern.compile(allowedPattern).matcher(value).matches()) {
                        return true;
                    }
                } catch (final PatternSyntaxException e) {
                    problems.add("The allowed LDAP URL pattern '" + allowedPattern
                                 + "' is not a valid regular expression: " + e.getDescription());
                }
            }
        }
        return false;
    }
}
