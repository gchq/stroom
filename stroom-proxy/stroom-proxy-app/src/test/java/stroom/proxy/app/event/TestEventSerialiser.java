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

package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.handler.ProxyReceiptIdGenerator;
import stroom.util.concurrent.UniqueId;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

public class TestEventSerialiser {

    @Test
    void test() throws IOException {
        final FeedKey feedKey = new FeedKey("test-feed", null);
        final AttributeMap attributeMap = new AttributeMap();
        attributeMap.put(StandardHeaderArguments.FEED, "test-feed");
        final String data = "this\nis some data \n with new \n\n lines";
        final EventSerialiser eventSerialiser = new EventSerialiser();
        final UniqueId receiptId = new ProxyReceiptIdGenerator(() -> "test-proxy").generateId();

        final String json = eventSerialiser.serialise(receiptId, feedKey, attributeMap, data);
        assertThat(json).contains("\"this\\nis some data \\n with new \\n\\n lines\"");
        assertThat(json).doesNotContain("\n");
    }
}
