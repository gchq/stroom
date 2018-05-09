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
 *
 */

package stroom.refdata.lmdb.eval;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import stroom.xml.event.EventListBuilder;
import stroom.xml.event.EventListBuilderFactory;
import stroom.xml.event.np.NPEventList;

public class TestNPEventListSerde {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestNPEventListSerde.class);


    @Test
    public void serializeDeserialize() {

        NPEventList npEventListInput = createEventList();

        NPEventListSerde serde = NPEventListSerde.instance();

        byte[] bytes = serde.serialize(npEventListInput);

        LOGGER.info("bytes.length Kryo {}", bytes.length);

        NPEventList npEventListOuput = serde.deserialize(bytes);

        Assertions.assertThat(npEventListInput).isEqualTo(npEventListOuput);
        Assertions.assertThat(npEventListInput.hashCode()).isEqualTo(npEventListOuput.hashCode());
    }

    private static NPEventList createEventList() {
        final EventListBuilder builder = EventListBuilderFactory.createBuilder();
        for (int i = 0; i < 100; i++) {
            try {
                builder.startDocument();
                builder.startPrefixMapping("t", "testuri");
                builder.startElement("testuri", "test", "test", null);
                String str = "testChars" + i;
                builder.characters(str.toCharArray(), 0, str.length());
                builder.endElement("testuri", "test", "test");
                builder.endDocument();
            } catch (final SAXException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        final NPEventList eventList = (NPEventList) builder.getEventList();
        builder.reset();

        return eventList;
    }

}