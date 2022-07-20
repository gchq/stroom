/*
 * Copyright 2017 Crown Copyright
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

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.servlet.http.HttpServletRequest;

@Singleton
public class EventResourceImpl implements EventResource {

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
        return receiveDataHelper.process(
                request,
                (req, attributeMap, requestUuid) -> consume(req, attributeMap, requestUuid, event),
                this::drop);
    }

    private void consume(final HttpServletRequest request,
                         final AttributeMap attributeMap,
                         final String requestUuid,
                         final String event) {
        eventStore.consume(attributeMap, requestUuid, event);
    }

    private void drop(final HttpServletRequest request,
                      final AttributeMap attributeMap,
                      final String requestUuid) {

    }
}
