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

package stroom.processor.impl;

import org.junit.jupiter.api.Disabled;


@Disabled
class TestProcessorTaskManagerRecentStreamDetails {
//    @Test
//    public void testSimple() {
//        final ProcessorFilter filter = new ProcessorFilter();
//        filter.setProcessorFilterTracker(new ProcessorFilterTracker());
//        filter.getProcessorFilterTracker().setMinMetaId(0L);
//        final OldFindStreamCriteria findStreamCriteria = new OldFindStreamCriteria();
//        findStreamCriteria.obtainFeeds().obtainInclude().add(1L);
//
//        // No history
//        ProcessorTaskManagerRecentStreamDetails details = new ProcessorTaskManagerRecentStreamDetails(null, 0L);
//
//        assertThat(details.hasRecentDetail()).isFalse();
//        assertThat(details.isApplicable(filter, findStreamCriteria)).isTrue();
//        filter.getProcessorFilterTracker().setMinMetaId(1L);
//
//        // Fake that 10 streams came in for feed 2
//        details = new ProcessorTaskManagerRecentStreamDetails(details, 10L);
//        details.addRecentFeedId(2L);
//
//        assertThat(details.hasRecentDetail()).isTrue();
//        assertThat(details.isApplicable(filter, findStreamCriteria)).isFalse();
//
//        // Now add some more for feed 1
//        details = new ProcessorTaskManagerRecentStreamDetails(details, 21L);
//        details.addRecentFeedId(1L);
//
//        assertThat(details.hasRecentDetail()).isTrue();
//        assertThat(details.isApplicable(filter, findStreamCriteria)).isTrue();
//
//        // Now add some more for feed 2
//        details = new ProcessorTaskManagerRecentStreamDetails(details, 23L);
//        details.addRecentFeedId(2L);
//        assertThat(details.isApplicable(filter, findStreamCriteria)).isFalse();
//    }
}
