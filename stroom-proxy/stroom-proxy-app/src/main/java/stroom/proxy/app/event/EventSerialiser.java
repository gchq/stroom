/*
 * Copyright 2024 Crown Copyright
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
import stroom.proxy.app.event.model.Event;
import stroom.proxy.app.event.model.Header;
import stroom.util.date.DateUtil;
import stroom.util.json.JsonUtil;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class EventSerialiser {

    public String serialise(final String requestUuid,
                            final String proxyId,
                            final FeedKey feedKey,
                            final AttributeMap attributeMap,
                            final String data) throws IOException {
        final List<Header> headers = attributeMap
                .entrySet()
                .stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new Header(entry.getKey().get(), entry.getValue()))
                .toList();

        final Event event = new Event(
                0,
                requestUuid,
                proxyId,
                feedKey.feed(),
                feedKey.type(),
                DateUtil.createNormalDateTimeString(),
                headers,
                data
        );

        return JsonUtil.writeValueAsString(event, false);
    }
}
