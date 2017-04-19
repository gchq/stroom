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

package stroom.node.client;

import stroom.dispatch.client.ClientDispatchAsync;
import stroom.widget.util.client.Future;
import stroom.widget.util.client.FutureImpl;
import stroom.node.shared.ClientProperties;
import stroom.node.shared.FetchClientPropertiesAction;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ClientPropertyCache {
    private final ClientDispatchAsync dispatcher;
    private ClientProperties clientProperties;

    @Inject
    public ClientPropertyCache(final ClientDispatchAsync dispatcher) {
        this.dispatcher = dispatcher;
    }

    public Future<ClientProperties> get() {
        final FutureImpl<ClientProperties> future = new FutureImpl<>();
        final ClientProperties props = clientProperties;
        if (props == null) {
            dispatcher.exec(new FetchClientPropertiesAction())
                    .onSuccess(result -> {
                        clientProperties = result;
                        future.setResult(result);
                    })
                    .onFailure(future::setThrowable);
        } else {
            future.setResult(props);
        }
        return future;
    }
}
