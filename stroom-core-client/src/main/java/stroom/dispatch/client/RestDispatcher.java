/*
 * Copyright 2019 Crown Copyright
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

package stroom.dispatch.client;

import stroom.widget.util.client.Console;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestException;
import org.fusesource.restygwt.client.Dispatcher;
import org.fusesource.restygwt.client.Method;

class RestDispatcher implements Dispatcher {

    @Override
    public Request send(final Method method, final RequestBuilder builder) throws RequestException {
        // Add CSRF header to all API requests — required by SecurityFilter
        builder.setHeader("X-CSRF", "1");

        // Never the body or the query string, which may hold a secret, e.g. a password
        Console.debug(() -> "Sending http request: " + builder.getHTTPMethod() + " "
                            + builder.getUrl().split("\\?")[0]
                            + ", timeout: " + builder.getTimeoutMillis());
        return builder.send();
    }
}
