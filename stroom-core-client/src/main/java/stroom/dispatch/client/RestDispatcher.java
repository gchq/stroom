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

package stroom.dispatch.client;

import com.google.gwt.core.client.GWT;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.logging.client.LogConfiguration;
import org.fusesource.restygwt.client.Dispatcher;
import org.fusesource.restygwt.client.Method;

import java.util.logging.Logger;

class RestDispatcher implements Dispatcher {

    @Override
    public Request send(final Method method, final RequestBuilder builder) throws RequestException {
        if (GWT.isClient() && LogConfiguration.loggingIsEnabled()) {
            final Logger logger = Logger.getLogger(
                    org.fusesource.restygwt.client.dispatcher.DefaultDispatcher.class.getName());
            logger.fine("Sending http request: " + builder.getHTTPMethod() + " " + builder.getUrl() +
                    " ,timeout:" + builder.getTimeoutMillis());

            final String content = builder.getRequestData();
            if (content != null && !content.isEmpty()) {
                logger.fine(content);
            }
        }
        return builder.send();
    }
}
