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

package stroom.db.migration;

import org.junit.Assert;
import org.junit.Test;
import stroom.util.io.StreamUtil;

import java.io.InputStream;
import java.sql.Connection;

public class TestEntityReferenceReplacer {
    @Test
    public void testEntityStreamType() {
        final String original = "<property><element>test</element><name>streamType</name><value><entity><type>StreamType</type><path>Events</path></entity></value></property>";
        final String expected = "<property><element>test</element><name>streamType</name><value><string>Events</string></value></property>";
        final String actual = new Replacer().replaceEntityReferences(null, original);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testReferenceStreamType() {
        final String original = "<reference><streamType><type>StreamType</type><path>Reference</path></streamType></reference>";
        final String expected = "<reference><streamType>Reference</streamType></reference>";
        final String actual = new Replacer().replaceEntityReferences(null, original);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testEntity1() {
        final String original = "<property><element>test</element><name>xslt</name><value><entity><type>XSLT</type><id>1234</id><path>/Test/Test XSLT</path></entity></value></property>";
        final String expected = "<property><element>test</element><name>xslt</name><value><entity><type>XSLT</type><uuid>uuid</uuid><name>Test XSLT</name></entity></value></property>";
        final String actual = new Replacer().replaceEntityReferences(null, original);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void testEntity2() {
        final String original = "<property><element>test</element><name>xslt</name><value><entity><type>XSLT</type><id>1234</id><name>Test XSLT</name></entity></value></property>";
        final String expected = "<property><element>test</element><name>xslt</name><value><entity><type>XSLT</type><uuid>uuid</uuid><name>Test XSLT</name></entity></value></property>";
        final String actual = new Replacer().replaceEntityReferences(null, original);

        Assert.assertEquals(expected, actual);
    }

    @Test
    public void fullTest() {
        final InputStream originalIS = getClass().getResourceAsStream("Sample.Pipeline.data.xml");
        final String original = StreamUtil.streamToString(originalIS);

        final InputStream updatedIS = getClass().getResourceAsStream("Updated.Pipeline.data.xml");
        final String updated = StreamUtil.streamToString(updatedIS);

        final String actual = new Replacer().replaceEntityReferences(null, original);

        Assert.assertEquals(updated, actual);
    }

    private class Replacer extends EntityReferenceReplacer {
        @Override
        String getUUID(Connection connection, String table, String id) {
            return "uuid";
        }
    }
}
