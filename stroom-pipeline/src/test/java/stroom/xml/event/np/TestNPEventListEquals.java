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

package stroom.xml.event.np;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;
import stroom.xml.event.EventList;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestNPEventListEquals extends StroomUnitTest {
    @Test
    public void test() throws SAXException {
        final EventList list1 = createList();
        final EventList list2 = createList();

        Assert.assertEquals(list1.hashCode(), list2.hashCode());
        Assert.assertTrue(list1.equals(list2));
    }

    private EventList createList() throws SAXException {
        final NPEventListBuilder builder = new NPEventListBuilder();

        final AttributesImpl atts = new AttributesImpl();
        atts.addAttribute("test", "TestAttribute", "TestAttribute", "string", "TestValue");
        final char[] ch = "This is a test string".toCharArray();

        builder.startElement("test", "TestElement", "TestElement", atts);
        builder.characters(ch, 0, ch.length);
        builder.endElement("test", "TestElement", "TestElement");

        final EventList eventList = builder.getEventList();
        builder.reset();

        return eventList;
    }
}
