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

package stroom.sqldatasource.impl;

import stroom.sqldatasource.shared.JdbcConnectionSettings;
import stroom.util.shared.NullSafe;

import com.mysql.cj.conf.ConnectionUrl;
import com.mysql.cj.conf.HostInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

/// Checks the JDBC settings of a SQL Data Source against the administrator's controls in
/// [SqlDataSourceConfig].
///
/// It is used when a document is saved, when its connection is tested and each time a connection
/// pool is built, so a document imported with a URL that is not allowed still cannot connect.
///
/// ## Driver properties
///
/// The names checked against the deny list are the ones the driver itself would use. A MySQL or
/// PostgreSQL URL is read by that driver's own URL parser, so a property written in any form it
/// accepts is found: percent-encoded names, MySQL's `(key=value)` and `address=(...)` host forms,
/// and so on. Stroom cannot read URLs for other drivers, so for those a property is any name after
/// `?`, `&` or `;`, and a URL containing `%` or `(` is refused.
///
/// User names and passwords are refused in the URL and the properties wherever they appear. They
/// belong in a Credential, so they are never stored in the document, exported or sent to the browser.
public final class JdbcConnectionValidator {

    // Embedded databases run inside Stroom's own JVM and can read or write its files, so they are
    // refused whatever the allow list says.
    static final List<String> EMBEDDED_DATABASE_URL_PREFIXES = List.of(
            "jdbc:h2:",
            "jdbc:hsqldb:",
            "jdbc:derby:",
            "jdbc:sqlite:");

    // Property names that hold a user name or password, in the drivers Stroom ships and common others.
    // Matched without regard to case.
    static final Set<String> CREDENTIAL_PROPERTY_NAMES = Set.of(
            "user",
            "username",
            "uid",
            "password",
            "password1",
            "password2",
            "password3",
            "pwd",
            "sslpassword");

    private static final String POSTGRESQL_URL_PREFIX = "jdbc:postgresql:";

    // A property set in a URL Stroom cannot read: a name after '?', '&' or ';' and followed by '='.
    // This covers the '?a=1&b=2' form and the SQL Server form (';a=1;b=2').
    private static final Pattern URL_PROPERTY_NAME_PATTERN = Pattern.compile("[?&;]\\s*([A-Za-z0-9_.\\-]+)\\s*=");

    // A user name or password before the host, as in 'jdbc:x://user:password@host/db', or in
    // Oracle's 'jdbc:oracle:thin:user/password@host' form.
    private static final Pattern URL_USER_INFO_PATTERN = Pattern.compile(
            "^[^?;]*//[^/?;]*@|^jdbc:oracle:[^:]+:[^@/:]+/[^@]*@",
            Pattern.CASE_INSENSITIVE);

    private JdbcConnectionValidator() {
        // Static methods only.
    }

    /// Finds every way the settings break the administrator's controls.
    ///
    /// @param settings The JDBC settings to check; `null` is reported as a missing URL.
    /// @param config   The administrator's controls.
    /// @return A message per problem, or an empty list if the settings may be used.
    public static List<String> findProblems(final JdbcConnectionSettings settings,
                                            final SqlDataSourceConfig config) {
        final List<String> problems = new ArrayList<>();
        final String url = NullSafe.get(settings, JdbcConnectionSettings::getUrl);
        if (NullSafe.isBlankString(url)) {
            problems.add("No JDBC URL has been set.");
            return problems;
        }

        final String trimmedUrl = url.trim();
        if (isEmbeddedDatabase(trimmedUrl)) {
            problems.add("Embedded databases (" + String.join(", ", EMBEDDED_DATABASE_URL_PREFIXES)
                         + ") cannot be used as SQL Data Sources.");
        } else if (!isAllowedUrl(trimmedUrl, config.getAllowedUrlPatterns(), problems)) {
            problems.add("The JDBC URL '" + trimmedUrl + "' does not match any pattern in the "
                         + "sqlDataSource.allowedUrlPatterns property. Ask an administrator to allow it.");
        }

        final List<String> usedNames = findPropertyNames(trimmedUrl, settings.getConnectionProperties(), problems);

        final Set<String> credentialNames = matching(usedNames, CREDENTIAL_PROPERTY_NAMES);
        if (!credentialNames.isEmpty() || URL_USER_INFO_PATTERN.matcher(trimmedUrl).find()) {
            final String names = credentialNames.isEmpty()
                    ? ""
                    : " (" + String.join(", ", credentialNames) + ")";
            problems.add("A user name or password cannot be set in the JDBC URL or connection properties"
                         + names + ". Choose a Credential instead, so the secret is not stored in the document.");
        }

        final Set<String> deniedNames = matching(usedNames, NullSafe.stream(config.getDeniedConnectionProperties())
                .filter(NullSafe::isNonBlankString)
                .map(String::trim)
                .collect(Collectors.toSet()));
        if (!deniedNames.isEmpty()) {
            problems.add("These driver properties are not allowed, as they can read local files, load "
                         + "classes, run code or weaken Stroom's controls: " + String.join(", ", deniedNames)
                         + ".");
        }
        return problems;
    }

    /// Checks the settings and throws if they break the administrator's controls.
    ///
    /// @param settings The JDBC settings to check.
    /// @param config   The administrator's controls.
    /// @throws IllegalArgumentException If there is any problem; its message lists them all.
    public static void validate(final JdbcConnectionSettings settings,
                                final SqlDataSourceConfig config) {
        final List<String> problems = findProblems(settings, config);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", problems));
        }
    }

    private static boolean isEmbeddedDatabase(final String url) {
        final String lowerCaseUrl = url.toLowerCase(Locale.ROOT);
        return EMBEDDED_DATABASE_URL_PREFIXES.stream()
                .anyMatch(lowerCaseUrl::startsWith);
    }

    private static boolean isAllowedUrl(final String url,
                                        final List<String> allowedUrlPatterns,
                                        final List<String> problems) {
        for (final String allowedUrlPattern : NullSafe.list(allowedUrlPatterns)) {
            if (NullSafe.isNonBlankString(allowedUrlPattern)) {
                try {
                    if (Pattern.compile(allowedUrlPattern).matcher(url).matches()) {
                        return true;
                    }
                } catch (final PatternSyntaxException e) {
                    problems.add("The allowed URL pattern '" + allowedUrlPattern
                                 + "' is not a valid regular expression: " + e.getDescription());
                }
            }
        }
        return false;
    }

    // Every property name the settings set, in the URL or the property list, as the driver sees them.
    private static List<String> findPropertyNames(final String url,
                                                  final Map<String, String> connectionProperties,
                                                  final List<String> problems) {
        final Map<String, String> properties = NullSafe.map(connectionProperties);
        final List<String> names = new ArrayList<>(properties.keySet());

        // The simple form is read for every URL, as a backstop to the drivers' own parsers.
        final Matcher matcher = URL_PROPERTY_NAME_PATTERN.matcher(url);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }

        if (ConnectionUrl.acceptsUrl(url)) {
            names.addAll(findMySqlPropertyNames(url, properties, problems));
        } else if (url.toLowerCase(Locale.ROOT).startsWith(POSTGRESQL_URL_PREFIX)) {
            names.addAll(findPostgreSqlPropertyNames(url, problems));
        } else if (url.indexOf('%') >= 0 || url.indexOf('(') >= 0) {
            problems.add("Stroom can only check the properties in MySQL and PostgreSQL URLs, so the URLs of "
                         + "other databases cannot contain '%' or '('. Set properties as connection "
                         + "properties instead.");
        }
        return names;
    }

    private static List<String> findMySqlPropertyNames(final String url,
                                                       final Map<String, String> properties,
                                                       final List<String> problems) {
        final List<String> names = new ArrayList<>();
        try {
            final Properties info = new Properties();
            info.putAll(properties);
            final ConnectionUrl connectionUrl = ConnectionUrl.getConnectionUrlInstance(url, info);
            names.addAll(connectionUrl.getOriginalProperties().keySet());
            for (final HostInfo host : connectionUrl.getHostsList()) {
                names.addAll(host.getHostProperties().keySet());
                if (NullSafe.isNonBlankString(host.getUser())) {
                    names.add("user");
                }
                if (NullSafe.isNonBlankString(host.getPassword())) {
                    names.add("password");
                }
            }
        } catch (final RuntimeException e) {
            problems.add("The MySQL driver cannot read the JDBC URL: " + e.getMessage());
        }
        return names;
    }

    private static List<String> findPostgreSqlPropertyNames(final String url, final List<String> problems) {
        final Properties parsed = org.postgresql.Driver.parseURL(url, null);
        if (parsed == null) {
            problems.add("The PostgreSQL driver cannot read the JDBC URL.");
            return List.of();
        }
        return new ArrayList<>(parsed.stringPropertyNames());
    }

    // The names in the list that are also in the set, compared without regard to case. A name the
    // driver repeats in another case is listed once, as first written.
    private static Set<String> matching(final List<String> names, final Set<String> set) {
        final Set<String> lowerCaseSet = set.stream()
                .map(name -> name.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        final Set<String> matches = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (final String name : names) {
            if (name != null && lowerCaseSet.contains(name.trim().toLowerCase(Locale.ROOT))) {
                matches.add(name.trim());
            }
        }
        return matches;
    }
}
