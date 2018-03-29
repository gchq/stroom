/*
 * Copyright 2018 Crown Copyright
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

package stroom.pipeline.server.xsltfunctions;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import stroom.pipeline.state.StreamHolder;
import stroom.streamstore.shared.Stream;
import stroom.util.date.DateUtil;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestFormatDate extends StroomUnitTest {
    @Test
    public void testDateWithNoYear() {
        final Stream stream = new Stream();
        stream.setCreateMs(DateUtil.parseNormalDateTimeString("2010-03-01T12:45:22.643Z"));

        final StreamHolder streamHolder = new StreamHolder();
        streamHolder.setStream(stream);

        final FormatDate formatDate = new FormatDate(streamHolder);

        Assert.assertEquals("2010-01-01T00:00:00.000Z", test(formatDate, "dd/MM", "01/01"));
        Assert.assertEquals("2009-04-01T00:00:00.000Z", test(formatDate, "dd/MM", "01/04"));
        Assert.assertEquals("2010-01-01T00:00:00.000Z", test(formatDate, "MM", "01"));
        Assert.assertEquals("2009-04-01T00:00:00.000Z", test(formatDate, "MM", "04"));
        Assert.assertEquals("2010-03-01T00:00:00.000Z", test(formatDate, "dd", "01"));
        Assert.assertEquals("2010-02-04T00:00:00.000Z", test(formatDate, "dd", "04"));
        Assert.assertEquals("2010-03-01T12:00:00.000Z", test(formatDate, "HH", "12"));
        Assert.assertEquals("2010-03-01T12:30:00.000Z", test(formatDate, "HH:mm", "12:30"));
    }

    private String test(final FormatDate formatDate, final String pattern, final String date) {
        return DateUtil.createNormalDateTimeString(formatDate.parseDate(null, "UTC", pattern, date));
    }
}
