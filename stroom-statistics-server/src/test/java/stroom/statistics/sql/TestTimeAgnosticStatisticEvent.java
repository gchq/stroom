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

package stroom.statistics.sql;

import org.apache.commons.lang.SerializationUtils;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class TestTimeAgnosticStatisticEvent {
    @Test
    public void serialiseTest() {
        final List<StatisticTag> tagList = new ArrayList<StatisticTag>();
        tagList.add(new StatisticTag("tag1", "val1"));
        tagList.add(new StatisticTag("tag2", "val2"));

        final TimeAgnosticStatisticEvent timeAgnosticStatisticEvent = TimeAgnosticStatisticEvent.createCount("MtStatName",
                tagList, 42L);

        // if we can't serialise the object then we should get an exception here
        final byte[] serializedForm = SerializationUtils.serialize(timeAgnosticStatisticEvent);

        final TimeAgnosticStatisticEvent timeAgnosticStatisticEvent2 = (TimeAgnosticStatisticEvent) SerializationUtils
                .deserialize(serializedForm);

        Assert.assertEquals(timeAgnosticStatisticEvent, timeAgnosticStatisticEvent2);
    }
}
