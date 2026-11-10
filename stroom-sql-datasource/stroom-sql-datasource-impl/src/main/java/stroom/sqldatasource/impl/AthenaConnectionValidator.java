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

import stroom.sqldatasource.shared.AthenaConnectionSettings;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/// Checks the Amazon Athena settings of a SQL Data Source against the administrator's controls in
/// [SqlDataSourceConfig].
///
/// The administrator's controls are:
///
/// - The region and workgroup, written `region/workgroup`, must match one of the
///   `allowedAthenaWorkGroups` patterns, as the workgroup carries the data scan limit.
/// - An output location, which every query writes its full result to, must match one of the
///   `allowedAthenaOutputLocations` patterns. With none, documents may not set one.
/// - A document must name a credential unless `allowDefaultAwsCredentials` lets it use Stroom's
///   own AWS identity, which any document creator could otherwise use.
///
/// It is used when a document is saved, when its connection is tested and before each query.
public final class AthenaConnectionValidator {

    // Such as 'eu-west-2', 'us-gov-west-1' or 'cn-north-1'.
    private static final Pattern REGION_PATTERN = Pattern.compile("^[a-z]{2}(-[a-z]+)+-\\d+$");

    private AthenaConnectionValidator() {
        // Static methods only.
    }

    /// Finds every way the settings break the administrator's controls.
    ///
    /// @param settings The Athena settings to check; `null` is reported as a missing region.
    /// @param config   The administrator's controls.
    /// @return A message per problem, or an empty list if the settings may be used.
    public static List<String> findProblems(final AthenaConnectionSettings settings,
                                            final SqlDataSourceConfig config) {
        final List<String> problems = new ArrayList<>();
        final String region = NullSafe.get(settings, AthenaConnectionSettings::getRegion);
        if (NullSafe.isBlankString(region)) {
            problems.add("No AWS region has been set.");
            return problems;
        }
        if (!REGION_PATTERN.matcher(region.trim()).matches()) {
            problems.add("'" + region + "' is not an AWS region, such as eu-west-2.");
        }

        final String workGroup = workGroup(settings);
        final String regionAndWorkGroup = region.trim() + "/" + workGroup;
        if (!isAllowed(regionAndWorkGroup, config.getAllowedAthenaWorkGroups(), problems)) {
            problems.add("The Athena workgroup '" + regionAndWorkGroup + "' does not match any pattern in the "
                         + "sqlDataSource.allowedAthenaWorkGroups property. Ask an administrator to allow it.");
        }

        final String outputLocation = settings.getOutputLocation();
        if (NullSafe.isNonBlankString(outputLocation)) {
            final String trimmedLocation = outputLocation.trim();
            if (!trimmedLocation.startsWith("s3://")) {
                problems.add("The output location must be an S3 URI, such as s3://bucket/path/.");
            } else if (!isAllowed(trimmedLocation, config.getAllowedAthenaOutputLocations(), problems)) {
                problems.add("The output location '" + trimmedLocation + "' does not match any pattern in the "
                             + "sqlDataSource.allowedAthenaOutputLocations property. Leave it empty to use the "
                             + "workgroup's, or ask an administrator to allow it.");
            }
        }

        if (NullSafe.isBlankString(settings.getCredentialName()) && !config.isAllowDefaultAwsCredentials()) {
            problems.add("Choose a credential holding an AWS access key. Stroom's own AWS identity can only be "
                         + "used if an administrator turns on sqlDataSource.allowDefaultAwsCredentials.");
        }
        return problems;
    }

    /// Checks the settings and throws if they break the administrator's controls.
    ///
    /// @param settings The Athena settings to check.
    /// @param config   The administrator's controls.
    /// @throws IllegalArgumentException If there is any problem; its message lists them all.
    public static void validate(final AthenaConnectionSettings settings,
                                final SqlDataSourceConfig config) {
        final List<String> problems = findProblems(settings, config);
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException(String.join("\n", problems));
        }
    }

    /// @param settings The Athena settings.
    /// @return The workgroup, or the default if none is set.
    static String workGroup(final AthenaConnectionSettings settings) {
        final String workGroup = settings.getWorkGroup();
        return NullSafe.isBlankString(workGroup)
                ? AthenaConnectionSettings.DEFAULT_WORK_GROUP
                : workGroup.trim();
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
                    problems.add("The allowed Athena pattern '" + allowedPattern
                                 + "' is not a valid regular expression: " + e.getDescription());
                }
            }
        }
        return false;
    }

    /// @param value        A setting, or `null`.
    /// @param defaultValue The value to use if it is not set.
    /// @return The setting, trimmed, or the default.
    static String orDefault(final String value, final String defaultValue) {
        return NullSafe.isBlankString(value)
                ? defaultValue
                : Objects.requireNonNull(value).trim();
    }
}
