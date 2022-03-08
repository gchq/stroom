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

import stroom.query.api.v2.QueryKey;
import stroom.security.client.api.event.LogoutEvent;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.web.bindery.event.shared.EventBus;
import com.sksamuel.gwt.websockets.Websocket;
import com.sksamuel.gwt.websockets.WebsocketListener;

import java.util.HashSet;
import java.util.Set;

@Singleton
public class ActiveQueries {

    private final Set<QueryKey> activeKeys = new HashSet<>();
    private final Websocket socket;
    private boolean open;

    @Inject
    public ActiveQueries(final EventBus eventBus) {
        // Listen for logout events.
        eventBus.addHandler(LogoutEvent.getType(), event -> close());

        String hostPageBaseUrl = GWT.getHostPageBaseURL();
        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));
        hostPageBaseUrl = hostPageBaseUrl.substring(0, hostPageBaseUrl.lastIndexOf("/"));

        int index = hostPageBaseUrl.indexOf("://");
        if (index != -1) {
            hostPageBaseUrl = hostPageBaseUrl.substring(index + 3);
        }

        socket = new Websocket("ws://" + hostPageBaseUrl + "/active-queries-ws");
        socket.addListener(new WebsocketListener() {
            @Override
            public void onClose() {
                // do something on close
            }

            @Override
            public void onMessage(String msg) {
                // a message is received
            }

            @Override
            public void onOpen() {
                // do something on open
            }
        });

        open();
    }

    private void open() {
        if (!open) {
            socket.open();
            open = true;
        }
    }

    private void close() {
        if (open) {
            socket.close();
            open = false;
        }
    }

    public void add(final QueryKey key) {
        open();

        if (activeKeys.add(key)) {
            socket.send("add:" + key.getUuid());
        }
    }

    public void remove(final QueryKey key) {
        open();

        socket.send("remove:" + key.getUuid());
        activeKeys.remove(key);
    }
}
