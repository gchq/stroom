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

package stroom.widget.util.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.Response;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class ResourceCache {

    private static final Map<String, String> cache = new HashMap<>();
    private static final Map<String, Set<Consumer<String>>> consumers = new HashMap<>();

    public static void get(final String url, final Consumer<String> consumer) {
        if (cache.containsKey(url)) {
            consumer.accept(cache.get(url));
        } else {
            if (consumers.containsKey(url)) {
                consumers.get(url).add(consumer);
                dispatchToConsumers(url);
            } else {
                consumers.computeIfAbsent(url, k -> new HashSet<>()).add(consumer);
                dispatchToConsumers(url);

                final RequestBuilder builder = new RequestBuilder(RequestBuilder.GET, url);
                try {
                    builder.sendRequest(null, new RequestCallback() {
                        @Override
                        public void onResponseReceived(final Request request, final Response response) {
                            GWT.log("ResourceCache received data from: '" + url + "'");

                            cache.put(url, response.getText());
                            dispatchToConsumers(url);
                        }

                        @Override
                        public void onError(final Request request, final Throwable exception) {
                            GWT.log(exception.getMessage(), exception);
                            cache.put(url, null);
                            dispatchToConsumers(url);
                        }
                    });
                } catch (final RequestException e) {
                    GWT.log(e.getMessage(), e);
                    cache.put(url, null);
                    dispatchToConsumers(url);
                }
            }
        }
    }

    private static void dispatchToConsumers(final String url) {
        if (cache.containsKey(url)) {
            final String data = cache.get(url);
            final Set<Consumer<String>> set = consumers.remove(url);
            if (set != null) {
                set.forEach(consumer -> consumer.accept(data));
            }
        }
    }
}
