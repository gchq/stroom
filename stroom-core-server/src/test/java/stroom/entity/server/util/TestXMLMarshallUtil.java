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

package stroom.entity.server.util;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.entity.shared.Period;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamtask.server.StreamProcessorFilterMarshaller;
import stroom.streamtask.shared.StreamProcessorFilter;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestXMLMarshallUtil extends StroomUnitTest {
    private static final StreamProcessorFilterMarshaller MARSHALLER = new StreamProcessorFilterMarshaller();

    @Test
    public void testSimple() {
        final FindStreamCriteria criteria1 = new FindStreamCriteria();
        criteria1.obtainStreamIdSet().add(999L);

        criteria1.obtainFeeds().obtainInclude().add(88L);
        criteria1.obtainFeeds().obtainInclude().add(889L);

        criteria1.obtainStreamIdSet().add(7L);
        criteria1.obtainStreamIdSet().add(77L);
        criteria1.obtainStreamIdSet().setMatchNull(true);

        criteria1.setCreatePeriod(new Period(1L, 2L));

        // Test Writing
        StreamProcessorFilter streamProcessorFilter = new StreamProcessorFilter();
        streamProcessorFilter.setFindStreamCriteria(criteria1);
        streamProcessorFilter = MARSHALLER.marshal(streamProcessorFilter);
        final String xml1 = streamProcessorFilter.getData();

        streamProcessorFilter = MARSHALLER.unmarshal(streamProcessorFilter);
        streamProcessorFilter = MARSHALLER.marshal(streamProcessorFilter);
        final String xml2 = streamProcessorFilter.getData();

        Assert.assertTrue(xml1.contains("999"));
        Assert.assertEquals(xml1, xml2);
    }

    @Test
    public void testShort() {
        StreamProcessorFilter streamProcessorFilter = new StreamProcessorFilter();
        streamProcessorFilter.setData(
                "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?><FindStreamCriteria></FindStreamCriteria>");
        streamProcessorFilter = MARSHALLER.unmarshal(streamProcessorFilter);

        final FindStreamCriteria criteria = streamProcessorFilter.getFindStreamCriteria();
        Assert.assertNotNull(criteria);
    }
}
