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

package stroom.dashboard.client.main;

import stroom.alert.client.event.AlertEvent;
import stroom.query.api.v2.QueryKey;
import stroom.security.client.api.event.LogoutEvent;
import stroom.util.client.Console;
import stroom.websocket.client.CloseEvent;
import stroom.websocket.client.ErrorEvent;
import stroom.websocket.client.MessageEvent;
import stroom.websocket.client.OpenEvent;
import stroom.websocket.client.WebSocket;
import stroom.websocket.client.WebSocket.ReadyState;
import stroom.websocket.client.WebSocketListener;
import stroom.websocket.client.WebSocketUtil;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;

import java.util.HashSet;
import java.util.Set;

@Singleton
public class ActiveQueries implements HasHandlers {

    private final EventBus eventBus;
    private final Set<QueryKey> activeKeys = new HashSet<>();
    private final WebSocket socket;

    @Inject
    public ActiveQueries(final EventBus eventBus) {
        this.eventBus = eventBus;

        try {
            // Listen for logout events.
            eventBus.addHandler(LogoutEvent.getType(), event -> close());
            final String url = WebSocketUtil.createWebSocketUrl("/active-queries-ws");

            Console.log("Using Web Socket URL: " + url);
            socket = new WebSocket(url, new WebSocketListener() {
                @Override
                public void onOpen(final OpenEvent event) {
                    // do something on open
                    Console.log("Opening web socket at " + url);
                }

                @Override
                public void onClose(final CloseEvent event) {
                    // do something on close
                    Console.log("Closing web socket at " + url);
                }

                @Override
                public void onMessage(final MessageEvent event) {
                    // a message is received
                    Console.log("Message received on web socket at " + url + " - [" + event.getData() + "]");
                }

                @Override
                public void onError(final ErrorEvent event) {
                    Console.log("Error on web socket at " + url);
                    AlertEvent.fireError(ActiveQueries.this,
                            "Error on web socket at " + url,
                            event.toString(),
                            null);
                }
            });
        } catch (final RuntimeException e) {
            error(e);
            throw e;
        }
    }

    private void close() {
        try {
            if (socket.getReadyState() == ReadyState.OPEN) {
                Console.log("Closing web socket");
                socket.close();
            }
        } catch (final RuntimeException e) {
            error(e);
        }
    }

    public void add(final QueryKey key) {
        try {
            if (activeKeys.add(key)) {
                final String msg = "add:" + key.getUuid();
                Console.log("Web socket send: [" + msg + "]");
                socket.send(msg);
            }
        } catch (final RuntimeException e) {
            error(e);
        }
    }

    public void remove(final QueryKey key) {
        try {
            final String msg = "remove:" + key.getUuid();
            Console.log("Web socket send: [" + msg + "]");
            socket.send(msg);
            activeKeys.remove(key);
        } catch (final RuntimeException e) {
            error(e);
        }
    }

    private void error(final RuntimeException e) {
        AlertEvent.fireError(ActiveQueries.this,
                e.getMessage(),
                e.toString(),
                null);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
