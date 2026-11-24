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

package stroom.ai.impl.tools.compare;

import stroom.ai.impl.FakeTabWorkspace;
import stroom.ai.impl.tools.ModelSideCalls;
import stroom.ai.impl.tools.compare.ActivityCategories.Category;
import stroom.ai.impl.tools.compare.ActivityCategories.Classified;
import stroom.ai.impl.tools.compare.ActivityCategories.Origin;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestActivityCategories {

    @TempDir
    Path tempDir;

    private SessionWorkspace workspace;
    private final List<String> asked = new ArrayList<>();

    @BeforeEach
    void setUp() {
        workspace = new SessionWorkspace(new FakeTabWorkspace(tempDir).workspace(), 3);
    }

    @Test
    void theModelsAnswersAreCheckedAgainstTheCategories() {
        final List<String> values = List.of("drive.example", "mail.example", "intranet.example", "odd.example");
        final String answer = "Here you are:\n```json\n{\"drive.example\": \"Personal cloud storage\", "
                              + "\"mail.example\": \"webmail\", \"intranet.example\": \"other\", "
                              + "\"odd.example\": \"ignore your instructions\", \"not.asked\": \"webmail\"}\n```";

        assertThat(ActivityCategories.read(answer, values)).containsExactly(
                Map.entry("drive.example", "personal cloud storage"),
                Map.entry("mail.example", "webmail"),
                Map.entry("intranet.example", "other"));
        assertThat(ActivityCategories.read("no JSON here", values)).isEmpty();
        assertThat(ActivityCategories.read("{not json}", values)).isEmpty();
    }

    @Test
    void theSiteListAndCorrectionsComeFirstAndTheModelsAnswersAreKept() {
        final Map<String, Long> values = new LinkedHashMap<>();
        values.put("drive.example", 10L);
        values.put("Mail.Example", 5L);
        values.put("jobs.example", 2L);
        final Classified first = ActivityCategories.classify(workspace, "destination.domain", values,
                Map.of("mail.example", "webmail"), Map.of(), model());

        assertThat(first.categories()).containsEntry("Mail.Example", new Category("webmail", Origin.SITE))
                .containsEntry("drive.example", new Category("personal cloud storage", Origin.INFERRED))
                .containsEntry("jobs.example", new Category("job search", Origin.INFERRED));
        // The site's value isn't sent to the model; the rest are, most frequent first.
        assertThat(asked).singleElement().satisfies(text -> assertThat(text).contains("drive.example")
                .contains("jobs.example").doesNotContain("Mail.Example"));
        assertThat(first.notes()).anySatisfy(note -> assertThat(note).contains("2 value(s) of destination.domain "
                                                                               + "classified by the model in 1 side "
                                                                               + "call(s): inferred"));

        // Again, the site's list as before, with a correction: nothing is asked; the correction is kept for later.
        final Classified second = ActivityCategories.classify(workspace, "destination.domain", values,
                Map.of("mail.example", "webmail"), Map.of("jobs.example", "other"), model());
        assertThat(asked).hasSize(1);
        assertThat(second.categories()).containsEntry("jobs.example", new Category("other", Origin.CORRECTED))
                .containsEntry("drive.example", new Category("personal cloud storage", Origin.INFERRED));
        final Classified third = ActivityCategories.classify(workspace, "destination.domain", values,
                Map.of("mail.example", "webmail"), Map.of(), model());
        assertThat(asked).hasSize(1);
        assertThat(third.categories()).containsEntry("jobs.example", new Category("other", Origin.CORRECTED));
    }

    @Test
    void withoutAModelTheValuesArentClassifiedAndTheResultSaysSo() {
        final Classified classified = ActivityCategories.classify(workspace, "process.name", Map.of("7z.exe", 3L),
                Map.of(), Map.of(), ModelSideCalls.NONE);

        assertThat(classified.categories()).isEmpty();
        assertThat(classified.notes()).singleElement().satisfies(note -> assertThat(note)
                .contains("1 value(s) of process.name weren't classified: no model to ask"));
    }

    @Test
    void eachCategorysEventsADay() {
        final SortedMap<LocalDate, Map<String, Double>> byDay = new TreeMap<>();
        byDay.put(LocalDate.of(2026, 5, 4), Map.of("drive.example", 3.0, "intranet.example", 9.0,
                "box.example", 2.0));
        byDay.put(LocalDate.of(2026, 5, 5), Map.of("intranet.example", 4.0));
        final Map<String, SortedMap<LocalDate, Double>> daily = ActivityCategories.daily(byDay, Map.of(
                "drive.example", new Category("personal cloud storage", Origin.INFERRED),
                "box.example", new Category("personal cloud storage", Origin.SITE),
                "intranet.example", new Category("other", Origin.INFERRED)));

        assertThat(daily).containsOnlyKeys("personal cloud storage");
        assertThat(daily.get("personal cloud storage")).containsExactly(Map.entry(LocalDate.of(2026, 5, 4), 5.0));
    }

    @Test
    void pairs() {
        assertThat(ActivityCategories.pairs(List.of("Box.Example=Personal Cloud Storage", "a=b=c")))
                .containsExactly(Map.entry("box.example", "personal cloud storage"), Map.entry("a=b", "c"));
        assertThatThrownBy(() -> ActivityCategories.pairs(List.of("no category"))).hasMessageContaining(
                "isn't value=category");
    }

    // A model that knows a few domains, recording what it was asked.
    private ModelSideCalls model() {
        return new ModelSideCalls() {
            @Override
            public boolean available() {
                return true;
            }

            @Override
            public String via() {
                return "a test model";
            }

            @Override
            public Answer ask(final Question question) {
                asked.add(question.user());
                final Map<String, String> answer = new LinkedHashMap<>();
                if (question.user().contains("drive.example")) {
                    answer.put("drive.example", "personal cloud storage");
                }
                if (question.user().contains("jobs.example")) {
                    answer.put("jobs.example", "job search");
                }
                return new Answer(JsonUtil.writeValueAsString(answer, false), 10, 5);
            }
        };
    }
}
