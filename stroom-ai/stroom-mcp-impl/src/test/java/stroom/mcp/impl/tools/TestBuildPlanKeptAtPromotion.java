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

import stroom.ai.impl.AiAuditLog;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/// Whether a promoted build holds nothing but what its promotion left in the workspace on purpose: then
/// build_status reports it promoted. Seen in e2e (TestE2ePromotionSwitchOver): the feed's survey, kept at
/// promotion, made the build read as a plan still to do, its next a copy_pipeline.
class TestBuildPlanKeptAtPromotion {

    private final FakeBuildFacts facts = new FakeBuildFacts();
    private final BuildPlan plan = new BuildPlan(facts, new BuildWorld().guard(),
            new Consent(() -> Mockito.mock(AiAuditLog.class)), new BuildMemory());

    @Test
    void aBuildHoldingOnlyItsKeptSurveyIsPromoted() {
        facts.docs = new ArrayList<>(List.of(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey")));
        facts.keptAtPromotion = Set.of("s");

        assertThat(plan.onlyKeptAtPromotion("b")).isTrue();
    }

    @Test
    void aBuildWithWorkOfItsOwnBesideWhatWasKeptIsNot() {
        // A pipeline made after the promotion, or a kept doc changed since (left out of what the facts call kept).
        facts.docs = new ArrayList<>(List.of(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey"),
                FakeBuildFacts.doc("Pipeline", "p", "ACME-V1.2-Events")));
        facts.keptAtPromotion = Set.of("s");
        assertThat(plan.onlyKeptAtPromotion("b")).isFalse();

        facts.docs = new ArrayList<>(List.of(FakeBuildFacts.doc("Documentation", "s", "ACME - Survey")));
        facts.keptAtPromotion = Set.of();
        assertThat(plan.onlyKeptAtPromotion("b")).isFalse();
    }

    @Test
    void anEmptyOrUnnamedBuildIsNotOneThisSays() {
        facts.docs = new ArrayList<>();
        assertThat(plan.onlyKeptAtPromotion("b")).isFalse();
        assertThat(plan.onlyKeptAtPromotion(null)).isFalse();
        assertThat(plan.onlyKeptAtPromotion(" ")).isFalse();
    }
}
