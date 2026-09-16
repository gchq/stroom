/*
 * Copyright 2022 Crown Copyright
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

import stroom.receive.common.InputStreamUtils;
import stroom.receive.common.ReceiveDataConfig;
import stroom.util.concurrent.UniqueId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

@Singleton
public class EventResourceImpl implements EventResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventResourceImpl.class);

    private final EventStore eventStore;
    private final ReceiveDataHelper receiveDataHelper;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;

    @Inject
    public EventResourceImpl(final EventStore eventStore,
                             final ReceiveDataHelper receiveDataHelper,
                             final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        this.eventStore = eventStore;
        this.receiveDataHelper = receiveDataHelper;
        this.receiveDataConfigProvider = receiveDataConfigProvider;
    }

    @Override
    public String event(final HttpServletRequest request,
                        final InputStream body) {
        final UniqueId receiptId = receiveDataHelper.process(request, (req, attributeMap, id) -> {
            LOGGER.debug("event() - receiptId: {}, attributeMap: {}", id, attributeMap);
            // Read only after authentication, and no more than a receipt may hold: a rolled file is
            // one receipt, and the receiver bounds a body by maxRequestSize.
            final String event;
            try (final InputStream bounded = InputStreamUtils.getBoundedInputStream(
                    body, receiveDataConfigProvider.get().getMaxRequestSize())) {
                event = new String(bounded.readAllBytes(), StandardCharsets.UTF_8);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
            eventStore.accept(attributeMap, id, event);
        });
        return receiptId.toString();
    }
}
