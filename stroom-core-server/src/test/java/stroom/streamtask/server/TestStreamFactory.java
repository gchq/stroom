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

package stroom.streamtask.server;

import stroom.util.test.StroomUnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import stroom.streamstore.server.StreamFactory;
import stroom.util.date.DateUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.zip.HeaderMap;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestStreamFactory extends StroomUnitTest {
    @Test
    public void testSimple() {
        final String testDate = "2000-01-01T00:00:00.000Z";
        final HeaderMap headerMap = new HeaderMap();
        headerMap.put("effectivetime", testDate);

        final Long time = StreamFactory.getReferenceEffectiveTime(headerMap, true);

        Assert.assertEquals(testDate, DateUtil.createNormalDateTimeString(time));
    }
}
