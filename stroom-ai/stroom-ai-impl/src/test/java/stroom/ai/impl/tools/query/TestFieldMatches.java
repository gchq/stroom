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

package stroom.ai.impl.tools.query;

import stroom.ai.impl.tools.query.FieldMatches.Candidate;
import stroom.ai.impl.tools.query.FieldMatches.Kind;
import stroom.ai.impl.tools.query.FieldMatches.Matched;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// A field named loosely, as a user names it: words split ignoring case, separators and camelCase, in any order,
/// partly, misspelt, or by what its description says; one clear match, several for the user to choose, or none
/// with the closest.
class TestFieldMatches {

    private static final List<String> EMAIL = List.of("Email/Sender/Id", "Email/Recipient/Id",
            "Email/Recipient/Name", "EventTime", "UserName", "source.ip", "IPAddress");

    @Test
    void aNameIsSplitIntoWordsIgnoringCaseSeparatorsAndCamelCase() {
        assertThat(FieldMatches.words("Email/Recipient/Id")).containsExactly("email", "recipient", "id");
        assertThat(FieldMatches.words("email.recipient_id")).containsExactly("email", "recipient", "id");
        assertThat(FieldMatches.words("IPAddress")).containsExactly("ip", "address");
        assertThat(FieldMatches.words("  Recipient - ID ")).containsExactly("recipient", "id");
        assertThat(FieldMatches.words("eventTime")).containsExactly("event", "time");
        assertThat(FieldMatches.words("")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({
            // Words of the name, whatever the separators and case.
            "email recipient id, Email/Recipient/Id",
            "EMAIL.RECIPIENT.ID, Email/Recipient/Id",
            // In any order.
            "id recipient email, Email/Recipient/Id",
            // Part of the path, the one field it fits.
            "Recipient ID, Email/Recipient/Id",
            "sender, Email/Sender/Id",
            // Misspelt.
            "Recipeint Id, Email/Recipient/Id",
            // Run together, or apart.
            "user name, UserName",
            "eventtime, EventTime",
            // A plural.
            "ip addresses, IPAddress"})
    void oneClearMatchIsThatField(final String query, final String field) {
        final Matched matched = FieldMatches.match(query, EMAIL, Map.of());

        assertThat(matched.kind()).as("%s", matched).isEqualTo(Kind.ONE);
        assertThat(matched.candidates()).extracting(Candidate::name).containsExactly(field);
    }

    @Test
    void severalAsCloseAreAllGivenRankedWithWhyNeverAGuess() {
        final Matched matched = FieldMatches.match("recipient", EMAIL, Map.of());

        assertThat(matched.kind()).isEqualTo(Kind.SEVERAL);
        assertThat(matched.candidates()).extracting(Candidate::name)
                .containsExactly("Email/Recipient/Id", "Email/Recipient/Name");
        assertThat(matched.candidates()).allSatisfy(candidate -> assertThat(candidate.why())
                .contains("its name has every word (recipient)"));
    }

    @Test
    void theSameFieldWrittenTwoWaysIsAskedAbout() {
        final Matched matched = FieldMatches.match("recipient id", List.of("Email/Recipient/Id",
                "email.recipient.id"), Map.of());

        assertThat(matched.kind()).isEqualTo(Kind.SEVERAL);
        assertThat(matched.candidates()).hasSize(2);
    }

    @Test
    void theFieldNamedWhollyIsChosenOverOnesHoldingMore() {
        // Recipient/Id names every word of the field; Email/Recipient/Id has another.
        final Matched matched = FieldMatches.match("recipient id", List.of("Email/Recipient/Id", "Recipient/Id"),
                Map.of());

        assertThat(matched.kind()).isEqualTo(Kind.ONE);
        assertThat(matched.candidates()).extracting(Candidate::name).containsExactly("Recipient/Id");
    }

    @Test
    void aFieldNamingOnlySomeOfTheWordsIsRankedByCoverageForTheUserToChoose() {
        final Matched matched = FieldMatches.match("recipient address", EMAIL, Map.of());

        assertThat(matched.kind()).isEqualTo(Kind.SEVERAL);
        assertThat(matched.candidates()).extracting(Candidate::name)
                .contains("Email/Recipient/Id", "Email/Recipient/Name", "IPAddress");
        assertThat(matched.candidates().getFirst().why()).contains("only");
    }

    @Test
    void theDescriptionsWordsAreTheFallback() {
        final Map<String, String> descriptions = Map.of(
                "Email/Recipient/Id", "The user who received the email.",
                "Email/Sender/Id", "The user who sent the email.");

        final Matched matched = FieldMatches.match("who received it", EMAIL, descriptions);

        assertThat(matched.kind()).isEqualTo(Kind.ONE);
        assertThat(matched.candidates().getFirst().name()).isEqualTo("Email/Recipient/Id");
        assertThat(matched.candidates().getFirst().why()).contains("its description has received");
        // Words that say nothing of which field match none.
        assertThat(FieldMatches.match("who is it", EMAIL, descriptions).kind()).isEqualTo(Kind.NONE);
    }

    @Test
    void descriptionsAsCloseAsEachOtherAreAskedAbout() {
        final Map<String, String> descriptions = Map.of(
                "Email/Recipient/Id", "The user the email was sent to.",
                "Email/Sender/Id", "The user who sent the email.");

        final Matched matched = FieldMatches.match("sent email", EMAIL, descriptions);

        assertThat(matched.kind()).isEqualTo(Kind.SEVERAL);
        assertThat(matched.candidates()).extracting(Candidate::name)
                .contains("Email/Recipient/Id", "Email/Sender/Id");
    }

    @Test
    void noMatchGivesTheClosestFewByName() {
        final Matched matched = FieldMatches.match("Hostname", EMAIL, Map.of());

        assertThat(matched.kind()).isEqualTo(Kind.NONE);
        assertThat(matched.candidates()).hasSizeLessThanOrEqualTo(FieldMatches.CLOSEST_GIVEN).isNotEmpty();
        assertThat(matched.candidates()).allSatisfy(candidate -> assertThat(candidate.why())
                .isEqualTo("the closest by name"));
    }

    @Test
    void shortWordsAreNotTakenForOthers() {
        // id is not ip: a misspelling is only matched on words of 4 letters or more.
        final Matched matched = FieldMatches.match("ip", List.of("Email/Recipient/Id", "source.ip"), Map.of());

        assertThat(matched.kind()).isEqualTo(Kind.ONE);
        assertThat(matched.candidates().getFirst().name()).isEqualTo("source.ip");
    }

    @Test
    void nothingGivenMatchesNothing() {
        assertThat(FieldMatches.match(" / ", EMAIL, Map.of()).kind()).isEqualTo(Kind.NONE);
        assertThat(FieldMatches.match("id", List.of(), Map.of()).candidates()).isEmpty();
    }
}
