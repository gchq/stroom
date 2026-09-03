/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.AnalyticRuleLevel;
import stroom.analytics.shared.AnalyticRuleStatus;
import stroom.analytics.shared.ReportDoc;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestRuleUtil {

    private static final String DESCRIPTION = "Internal notes that must not leave Stroom.";

    @Test
    void getDetailedDescription_included() {
        final AnalyticRuleDoc doc = buildRule(true);

        assertThat(RuleUtil.getDetailedDescription(doc))
                .isEqualTo(DESCRIPTION);
    }

    @Test
    void getDetailedDescription_notIncluded() {
        final AnalyticRuleDoc doc = buildRule(false);

        assertThat(RuleUtil.getDetailedDescription(doc))
                .isNull();
    }

    @Test
    void getDetailedDescription_includedByDefault() {
        // The default is to include, so a rule that has never touched the setting behaves as it always did.
        final AnalyticRuleDoc doc = AnalyticRuleDoc.builder()
                .uuid("test-uuid")
                .description(DESCRIPTION)
                .build();

        assertThat(RuleUtil.getDetailedDescription(doc))
                .isEqualTo(DESCRIPTION);
    }

    @Test
    void getDetailedDescription_nullDoc() {
        assertThat(RuleUtil.getDetailedDescription(null))
                .isNull();
    }

    @Test
    void getDetailedDescription_noDescription() {
        // AbstractAnalyticRuleDoc normalises a null description to empty, so that is what comes back.
        final AnalyticRuleDoc doc = AnalyticRuleDoc.builder()
                .uuid("test-uuid")
                .includeRuleDocumentation(true)
                .build();

        assertThat(RuleUtil.getDetailedDescription(doc))
                .isEmpty();
    }

    @Test
    void getLevel() {
        final AnalyticRuleDoc doc = AnalyticRuleDoc.builder()
                .uuid("test-uuid")
                .level(AnalyticRuleLevel.HIGH)
                .build();

        // The display value goes on the detection, not the enum name.
        assertThat(RuleUtil.getLevel(doc))
                .isEqualTo("High");
    }

    @Test
    void getLevel_notSet() {
        // A rule need not declare a level, in which case no level element is written to the detection.
        assertThat(RuleUtil.getLevel(buildRule(true)))
                .isNull();
    }

    @Test
    void getLevel_report() {
        // Only an analytic rule has a level. A report produces no detections, so it has none.
        assertThat(RuleUtil.getLevel(ReportDoc.builder().uuid("test-uuid").build()))
                .isNull();
    }

    @Test
    void getLevel_nullDoc() {
        assertThat(RuleUtil.getLevel(null))
                .isNull();
    }

    @Test
    void getStatus() {
        final AnalyticRuleDoc doc = AnalyticRuleDoc.builder()
                .uuid("test-uuid")
                .status(AnalyticRuleStatus.STABLE)
                .build();

        assertThat(RuleUtil.getStatus(doc))
                .isEqualTo("Stable");
    }

    @Test
    void getStatus_notSet() {
        assertThat(RuleUtil.getStatus(buildRule(true)))
                .isNull();
    }

    @Test
    void getStatus_report() {
        assertThat(RuleUtil.getStatus(ReportDoc.builder().uuid("test-uuid").build()))
                .isNull();
    }

    @Test
    void getStatus_nullDoc() {
        assertThat(RuleUtil.getStatus(null))
                .isNull();
    }

    private AnalyticRuleDoc buildRule(final boolean includeRuleDocumentation) {
        return AnalyticRuleDoc.builder()
                .uuid("test-uuid")
                .description(DESCRIPTION)
                .includeRuleDocumentation(includeRuleDocumentation)
                .build();
    }
}
