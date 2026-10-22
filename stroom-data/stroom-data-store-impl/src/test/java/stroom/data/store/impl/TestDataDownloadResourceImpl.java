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

package stroom.data.store.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.event.logging.mock.MockStroomEventLoggingService;
import stroom.meta.shared.FindMetaCriteria;

import event.logging.Criteria;
import event.logging.Event;
import event.logging.EventLoggerBuilder.TypeIdStep;
import event.logging.ExportEventAction;
import event.logging.impl.DefaultEventLoggingService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDataDownloadResourceImpl {

    /// Regression test: the criteria were converted for the audit log before the logged work
    /// started, so if that conversion threw, the download failed with no audit event at all.
    @Test
    void testACriteriaConversionFailureIsStillAudited() {
        final List<Event> loggedEvents = new ArrayList<>();
        // The library's real builder, which logs, in place of the mock's, which does not.
        final DefaultEventLoggingService capturingService = new DefaultEventLoggingService() {
            @Override
            public void log(final Event event) {
                loggedEvents.add(event);
            }
        };
        final MockStroomEventLoggingService eventLoggingService = new MockStroomEventLoggingService() {
            @Override
            public TypeIdStep loggedWorkBuilder() {
                return capturingService.loggedWorkBuilder();
            }

            @Override
            public Criteria convertExpressionCriteria(final String type,
                                                      final ExpressionCriteria expressionCriteria) {
                throw new IllegalStateException("Unable to convert criteria");
            }
        };
        final DataDownloadResourceImpl resource = new DataDownloadResourceImpl(
                () -> eventLoggingService,
                () -> {
                    throw new AssertionError("Nothing should be downloaded");
                },
                () -> {
                    throw new AssertionError("Nothing should be downloaded");
                });

        assertThatThrownBy(() -> resource.downloadZip(new FindMetaCriteria()))
                .hasMessageContaining("Unable to convert criteria");

        assertThat(loggedEvents).hasSize(1);
        assertThat(loggedEvents.getFirst().getEventDetail().getEventAction())
                .isInstanceOfSatisfying(ExportEventAction.class, action ->
                        assertThat(action.getOutcome().isSuccess())
                                .as("the attempt is recorded as a failure")
                                .isFalse());
    }
}
