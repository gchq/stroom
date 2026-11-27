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

package stroom.mcp.impl.tools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/// The names a new version is proposed, from the version token in the original's, whatever its shape.
class TestVersionNames {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Acme-Events-V1.0-Indexing | Acme-Events-V1.1-Indexing | Acme-Events-V2.0-Indexing",
            "Acme-Door-V1.2-Events     | Acme-Door-V1.3-Events     | Acme-Door-V2.0-Events",
            "ACME-V1 - Indexing        | ACME-V2 - Indexing        | ACME-V2 - Indexing",
            "acme-events-v1.0          | acme-events-v1.1          | acme-events-v2.0",
            "Acme_v1.2.9               | Acme_v1.2.10              | Acme_v2.0.0",
            // The last token is the version: an earlier one is part of the name.
            "V2-Gateway-V1.4           | V2-Gateway-V1.5           | V2-Gateway-V2.0",
            // No version token (Dev1 and IPv4 are words, not versions): the name is V1.0, as promotion files it,
            // so its next version is V1.1 and its next major V2.0.
            "Acme-Dev1-IPv4-Events     | Acme-Dev1-IPv4-Events-V1.1 | Acme-Dev1-IPv4-Events-V2.0",
            "Acme-Events-Indexing      | Acme-Events-Indexing-V1.1  | Acme-Events-Indexing-V2.0",
            "Acme-{feed}-Indexing      | Acme-V1.1-{feed}-Indexing  | Acme-V2.0-{feed}-Indexing"})
    void theNextVersionBumpsTheLastPartAndTheMajorTheFirst(final String name, final String next, final String major) {
        assertThat(VersionNames.next(name)).isEqualTo(next);
        assertThat(VersionNames.nextMajor(name)).isEqualTo(major);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Its version token changed for a newer one, in its place.
            "ACME-V1.0-Events      | ACME-V1.1-Events       | true",
            "ACME-V1.0-Events      | ACME-V2.0-Events       | true",
            "ACME-V1.0-Events      | ACME-v1.10-Events      | true",
            "acme-events-v1        | acme-events-v2         | true",
            // No token: -V<n> after it, newer than the V1.0 it is taken to be.
            "EVAL-102-Events       | EVAL-102-Events-V1.1   | true",
            "EVAL-102-Events       | EVAL-102-Events-v2     | true",
            "Acme-{feed}-Indexing  | Acme-V1.1-{feed}-Indexing | true",
            // Seen in eval cases 97 and 102-104 on Haiku: the version placed elsewhere.
            "EVAL-102-Events       | EVAL-102-V1.1-Events   | false",
            "EVAL-97-Events        | EVAL-97-Events V1.1    | false",
            "EVAL-97-Events        | EVAL-97-Events_V1.1    | false",
            // Not newer: the original's own version, or an older one.
            "EVAL-105-Events       | EVAL-105-Events-V1.0   | false",
            "ACME-V1.1-Events      | ACME-V1.1-Events       | false",
            "ACME-V1.1-Events      | ACME-V1.0-Events       | false",
            // Anything else changed, or no version at all.
            "ACME-V1.0-Events      | ACME-V1.1-Event        | false",
            "ACME-V1.0-Events      | ACME-Events            | false",
            "ACME-V1.0-Events      | ACME-V1.1-Events-V1.1  | false",
            "EVAL-102-Events       | EVAL-102-Events-       | false"})
    void aNewVersionsNameIsTheOriginalsWithANewerVersionInItsPlace(final String original,
                                                                    final String candidate,
                                                                    final boolean newer) {
        assertThat(VersionNames.namesNewerVersion(original, candidate, List.of())).isEqualTo(newer);
    }

    @Test
    void aVersionTokenInAFeedsNameIsLeftToTheFeed() {
        // The feed CISCO-ASA-V9's token isn't the pipeline's: its new version gets -V1.1 after its name.
        assertThat(VersionNames.namesNewerVersion("CISCO-ASA-V9-EVENTS", "CISCO-ASA-V9-EVENTS-V1.1",
                List.of("CISCO-ASA-V9"))).isTrue();
        assertThat(VersionNames.namesNewerVersion("CISCO-ASA-V9-EVENTS", "CISCO-ASA-V10-EVENTS",
                List.of("CISCO-ASA-V9"))).isFalse();
        assertThat(VersionNames.namesNewerVersion(null, "X-V1.1", List.of())).isFalse();
        assertThat(VersionNames.namesNewerVersion("X", null, List.of())).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Acme-Events-Indexing | Acme-Events-Indexing-V1.1",
            "Acme security        | Acme security-V1.1"})
    void aNameWithNoVersionIsTakenAsTheFirstVersionPromotionFilesItUnder(final String name, final String next) {
        // A promoted pipeline with no version token is laid out in V1.0, so its next version is V1.1, the next
        // version's name holding the token its folder is named by.
        assertThat(VersionNames.next(name)).isEqualTo(next);
        assertThat(VersionNames.token(VersionNames.next(name))).contains("V1.1");
        assertThat(VersionNames.token(VersionNames.nextMajor(name))).contains("V2.0");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "acme-events-v1    | acme-events-v2",
            "acme_events_v9    | acme_events_v10",
            "v3-acme           | v4-acme",
            "acme-{type}-v1    | acme-{type}-v2",
            "acme-events       | acme-events-v2",
            // Before the first variable, not after what it stands for.
            "acme-events{_suffix} | acme-events-v2{_suffix}",
            "{feed}-events     | {feed}-events-v2",
            "acme-{type}-events | acme-v2-{type}-events",
            "ecs-acme-dev1     | ecs-acme-dev1-v2"})
    void anIndexNamesVersionGoesUpOrIsAdded(final String index, final String next) {
        assertThat(VersionNames.nextIndexName(index)).isEqualTo(next);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // The pipeline's change, where the document holds the same token.
            "Acme-Events-V1.0-XSLT | V1.0 | V1.1 | Acme-Events-V1.1-XSLT",
            // Its own token otherwise.
            "Acme-Common-V3        | V1.0 | V1.1 | Acme-Common-V4",
            "Acme helpers          | V1.0 | V1.1 | Acme helpers-V1.1"})
    void aDocumentFollowsThePipelinesVersionChange(final String name,
                                                   final String from,
                                                   final String to,
                                                   final String expected) {
        assertThat(VersionNames.following(name, from, to)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // A promoted module with no version of its own is V1.0.
            "ACME common              | ACME common                                | ACME common V1.1",
            // The highest of those known: the one changed, the feed's newest.
            "ACME common              | ACME common;ACME common V1.1               | ACME common V1.2",
            "ACME common              | ACME common V1.9;ACME common V1.10         | ACME common V1.11",
            "ACME helpers             | ACME helpers V2                            | ACME helpers V3",
            // A feed's own version is part of the base, not the module's.
            "ACME-V2 common           | ACME-V2 common                             | ACME-V2 common V1.1",
            // Another module's name is passed over.
            "ACME common              | ACME helpers V1.5;ACME common              | ACME common V1.1"})
    void aSharedModulesNewVersionIsAfterTheHighestKnown(final String base, final String names, final String next) {
        assertThat(VersionNames.nextModule(base, List.of(names.split(";")))).isEqualTo(next);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ACME common | ACME common      | true",
            "ACME common | ACME common V1.1 | true",
            "ACME common | ACME common-V2   | false",
            "ACME common | ACME commonV1.1  | false",
            "ACME common | ACME common copy | false",
            "ACME common | ACME-V2 common   | false"})
    void aSharedModulesNameIsItsBaseOrABaseAndVersion(final String base, final String name, final boolean is) {
        assertThat(VersionNames.isModuleName(base, name)).isEqualTo(is);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "ACME common          | V1.0",
            "ACME common V1.1     | V1.1",
            "ACME-V2 helpers      | V1.0",
            "ACME-V2 helpers v1.3 | v1.3"})
    void aSharedModulesVersionFolderIsTheVersionAfterItsNameOrV10(final String name, final String version) {
        // Decided by the user (2026-11-17): a module filed under <feed>/Common/<version>, the first one V1.0.
        assertThat(VersionNames.moduleVersion(name)).isEqualTo(version);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Acme-v20261117093000123456",
            "Acme-v1.20261117093000123456",
            "Acme-v1234567890"})
    void aTokenWhoseNumbersAreTooLongIsNoVersion(final String name) {
        // Seen in review: a timestamp-like run of digits overflowed a long and failed copy_pipeline.
        assertThat(VersionNames.token(name)).isEmpty();
        assertThat(VersionNames.next(name)).isEqualTo(name + "-V1.1");
        assertThat(VersionNames.nextIndexName(name)).isEqualTo(name + "-v2");
        assertThat(VersionNames.nextModule(name, List.of(name + " V1.20261117093000123456")))
                .isEqualTo(name + " V1.1");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // The feed's own token is part of the name, not the pipeline's version: with no other, it is
            // unversioned (V1.0), its next version -V1.1.
            "CISCO-ASA-V9-EVENTS        | CISCO-ASA-V9 |      | CISCO-ASA-V9-EVENTS-V1.1",
            "cisco-asa-v9 Events        | CISCO-ASA-V9 |      | cisco-asa-v9 Events-V1.1",
            // A token of its own beside the feed's is the version.
            "CISCO-ASA-V9-EVENTS-V1.2   | CISCO-ASA-V9 | V1.2 | CISCO-ASA-V9-EVENTS-V1.3",
            "V2 Gateway-V1.4            | V2 Gateway   | V1.4 | V2 Gateway-V1.5",
            // A token outside the feed's name, though it is the same token, is the version.
            "ACME-V2 V2                 | ACME-V2      | V2   | ACME-V2 V3"})
    void aTokenInsideTheFeedsNameIsNotThePipelinesVersion(final String name,
                                                          final String feed,
                                                          final String token,
                                                          final String next) {
        // Found in review (2026-11-18): CISCO-ASA-V9-EVENTS of feed CISCO-ASA-V9 was taken as V9, its next version
        // V10, renaming the feed's part.
        assertThat(VersionNames.token(name, List.of(feed))).isEqualTo(Optional.ofNullable(token));
        assertThat(VersionNames.next(name, List.of(feed))).isEqualTo(next);
    }

    @Test
    void aDocumentFollowingAVersionChangeKeepsTheFeedsOwnToken() {
        assertThat(VersionNames.following("CISCO-ASA-V9-EVENTS-V1.0-XSLT", "V1.0", "V1.1", List.of("CISCO-ASA-V9")))
                .isEqualTo("CISCO-ASA-V9-EVENTS-V1.1-XSLT");
        // The pipeline's change is V9 to V10 in name only: the feed's V9 is left, the document's own bumped.
        assertThat(VersionNames.following("CISCO-ASA-V9-XSLT", "V9", "V10", List.of("CISCO-ASA-V9")))
                .isEqualTo("CISCO-ASA-V9-XSLT-V1.1");
        assertThat(VersionNames.nextMajor("CISCO-ASA-V9-EVENTS", List.of("CISCO-ASA-V9")))
                .isEqualTo("CISCO-ASA-V9-EVENTS-V2.0");
    }
}
