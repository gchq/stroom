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

import stroom.dataconnection.shared.AthenaConnectionSettings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAthenaConnectionValidator {

    private static DataConnectionConfig config(final String... allowedWorkGroups) {
        return new DataConnectionConfig(null, null, null, null, null, null, null, null, List.of(allowedWorkGroups),
                List.of("^s3://stroom-results/.*$"), true, null, null);
    }

    private static AthenaConnectionSettings settings(final String region, final String workGroup) {
        return AthenaConnectionSettings.builder()
                .region(region)
                .workGroup(workGroup)
                .build();
    }

    @Test
    void allowedWorkGroupIsAccepted() {
        assertThat(AthenaConnectionValidator.findProblems(
                settings("eu-west-2", "stroom-reports"),
                config("^us-east-1/.*$", "^eu-west-2/stroom-.*$")))
                .isEmpty();
    }

    @Test
    void noWorkGroupIsAllowedWithoutPatterns() {
        // Otherwise any document editor could run queries with Stroom's own AWS identity.
        assertThat(AthenaConnectionValidator.findProblems(settings("eu-west-2", "primary"), config()))
                .singleElement()
                .asString()
                .contains("'eu-west-2/primary'")
                .contains("allowedAthenaWorkGroups");
    }

    @Test
    void missingWorkGroupIsCheckedAsTheDefault() {
        assertThat(AthenaConnectionValidator.findProblems(
                settings("eu-west-2", null),
                config("^eu-west-2/primary$")))
                .isEmpty();
    }

    @Test
    void patternMustMatchTheWholeRegionAndWorkGroup() {
        assertThat(AthenaConnectionValidator.findProblems(
                settings("eu-west-2", "stroom-reports-and-more"),
                config("eu-west-2/stroom-reports")))
                .hasSize(1);
    }

    @Test
    void missingRegionIsReported() {
        assertThat(AthenaConnectionValidator.findProblems(settings(" ", "primary"), config(".*")))
                .containsExactly("No AWS region has been set.");
        assertThat(AthenaConnectionValidator.findProblems(null, config(".*")))
                .containsExactly("No AWS region has been set.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"london", "EU-WEST-2", "eu-west", "eu-west-2/primary"})
    void somethingThatIsNotARegionIsReported(final String region) {
        assertThat(AthenaConnectionValidator.findProblems(settings(region, "primary"), config(".*")))
                .anySatisfy(problem -> assertThat(problem).contains("is not an AWS region"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"eu-west-2", "us-gov-west-1", "cn-north-1", "ap-southeast-4"})
    void realRegionsAreAccepted(final String region) {
        assertThat(AthenaConnectionValidator.findProblems(settings(region, "primary"), config(".*"))).isEmpty();
    }

    @Test
    void outputLocationMustBeAnS3Uri() {
        final AthenaConnectionSettings settings = AthenaConnectionSettings.builder()
                .region("eu-west-2")
                .outputLocation("https://example.com/results")
                .build();

        assertThat(AthenaConnectionValidator.findProblems(settings, config(".*")))
                .singleElement()
                .asString()
                .contains("S3 URI");
    }

    @Test
    void allowedOutputLocationIsAccepted() {
        final AthenaConnectionSettings settings = AthenaConnectionSettings.builder()
                .region("eu-west-2")
                .outputLocation("s3://stroom-results/athena/")
                .build();

        assertThat(AthenaConnectionValidator.findProblems(settings, config(".*"))).isEmpty();
    }

    @Test
    void outputLocationNotAllowedIsRefused() {
        // Every query writes its full result there, with the querying user's AWS identity.
        final AthenaConnectionSettings settings = AthenaConnectionSettings.builder()
                .region("eu-west-2")
                .outputLocation("s3://somebody-elses-bucket/")
                .build();

        assertThat(AthenaConnectionValidator.findProblems(settings, config(".*")))
                .singleElement()
                .asString()
                .contains("allowedAthenaOutputLocations");
    }

    @Test
    void noOutputLocationMayBeSetWithoutPatterns() {
        final AthenaConnectionSettings settings = AthenaConnectionSettings.builder()
                .region("eu-west-2")
                .outputLocation("s3://stroom-results/athena/")
                .build();
        final DataConnectionConfig config = new DataConnectionConfig(
                null, null, null, null, null, null, null, null, List.of(".*"), null, true, null, null);

        assertThat(AthenaConnectionValidator.findProblems(settings, config))
                .singleElement()
                .asString()
                .contains("allowedAthenaOutputLocations");
    }

    @Test
    void stroomsOwnIdentityNeedsTheAdministratorsSwitch() {
        // Otherwise any document creator could query whatever Stroom's own AWS role can read.
        final DataConnectionConfig switchedOff = new DataConnectionConfig(
                null, null, null, null, null, null, null, null, List.of(".*"), null, null, null, null);

        assertThat(AthenaConnectionValidator.findProblems(settings("eu-west-2", "primary"), switchedOff))
                .singleElement()
                .asString()
                .contains("allowDefaultAwsCredentials");
        assertThat(AthenaConnectionValidator.findProblems(
                AthenaConnectionSettings.builder()
                        .region("eu-west-2")
                        .credentialName("athena-reader")
                        .build(),
                switchedOff))
                .as("a document naming a credential needs no switch")
                .isEmpty();
    }

    @Test
    void invalidPatternIsReportedAndDoesNotAllow() {
        assertThat(AthenaConnectionValidator.findProblems(settings("eu-west-2", "primary"), config("[unclosed")))
                .hasSize(2);
    }

    @Test
    void validateThrowsWithEveryProblem() {
        assertThatThrownBy(() -> AthenaConnectionValidator.validate(settings("london", "primary"), config()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is not an AWS region")
                .hasMessageContaining("allowedAthenaWorkGroups");
    }
}
