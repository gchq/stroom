/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamtask;

import org.junit.jupiter.api.Disabled;
import stroom.test.common.util.test.StroomUnitTest;


@Disabled
class TestStreamTaskCreatorRecentStreamDetails extends StroomUnitTest {
//    @Test
//    public void testSimple() {
//        final ProcessorFilter filter = new ProcessorFilter();
//        filter.setStreamProcessorFilterTracker(new ProcessorFilterTracker());
//        filter.getStreamProcessorFilterTracker().setMinStreamId(0L);
//        final OldFindStreamCriteria findStreamCriteria = new OldFindStreamCriteria();
//        findStreamCriteria.obtainFeeds().obtainInclude().add(1L);
//
//        // No history
//        StreamTaskCreatorRecentStreamDetails details = new StreamTaskCreatorRecentStreamDetails(null, 0L);
//
//        assertThat(details.hasRecentDetail()).isFalse();
//        assertThat(details.isApplicable(filter, findStreamCriteria)).isTrue();
//        filter.getStreamProcessorFilterTracker().setMinStreamId(1L);
//
//        // Fake that 10 streams came in for feed 2
//        details = new StreamTaskCreatorRecentStreamDetails(details, 10L);
//        details.addRecentFeedId(2L);
//
//        assertThat(details.hasRecentDetail()).isTrue();
//        assertThat(details.isApplicable(filter, findStreamCriteria)).isFalse();
//
//        // Now add some more for feed 1
//        details = new StreamTaskCreatorRecentStreamDetails(details, 21L);
//        details.addRecentFeedId(1L);
//
//        assertThat(details.hasRecentDetail()).isTrue();
//        assertThat(details.isApplicable(filter, findStreamCriteria)).isTrue();
//
//        // Now add some more for feed 2
//        details = new StreamTaskCreatorRecentStreamDetails(details, 23L);
//        details.addRecentFeedId(2L);
//        assertThat(details.isApplicable(filter, findStreamCriteria)).isFalse();
//    }
}
