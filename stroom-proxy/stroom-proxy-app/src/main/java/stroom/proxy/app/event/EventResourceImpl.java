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
import stroom.util.concurrent.UniqueId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;

@Singleton
public class EventResourceImpl implements EventResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventResourceImpl.class);

    private final EventStore eventStore;
    private final ReceiveDataHelper receiveDataHelper;

    @Inject
    public EventResourceImpl(final EventStore eventStore,
                             final ReceiveDataHelper receiveDataHelper) {
        this.eventStore = eventStore;
        this.receiveDataHelper = receiveDataHelper;
    }

    @Override
    public String event(final HttpServletRequest request,
                        final String event) {
        final UniqueId receiptId = receiveDataHelper.process(
                request,
                (req, attributeMap, receiptId2) ->
                        consume(attributeMap, receiptId2, event),
                this::drop);
        return receiptId.toString();
    }

    private void consume(final AttributeMap attributeMap,
                         final UniqueId receiptId,
                         final String event) {
        LOGGER.debug("consume() - receiptId: {}, attributeMap: {}\n{}", receiptId, attributeMap, event);
        eventStore.consume(attributeMap, receiptId, event);
    }

    private void drop(final HttpServletRequest request,
                      final AttributeMap attributeMap,
                      final UniqueId receiptId) {
        LOGGER.debug("drop() - receiptId: {}, attributeMap: {}", receiptId, attributeMap);
    }
}
