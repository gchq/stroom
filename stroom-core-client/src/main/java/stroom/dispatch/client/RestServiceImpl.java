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

package stroom.dispatch.client;

import com.google.gwt.core.client.GWT;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestCallback;
import com.google.gwt.http.client.RequestException;
import com.google.gwt.http.client.URL;
import com.google.web.bindery.event.shared.EventBus;
import stroom.alert.client.event.AlertEvent;

import javax.inject.Inject;

public class RestServiceImpl implements RestService {
    private final EventBus eventBus;

    @Inject
    public RestServiceImpl(final EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public RestService.Response send(final String path, final String json) {
        final ResponseImpl response = new ResponseImpl(eventBus);

        try {
            final String url = GWT.getHostPageBaseURL() + path;
            final RequestBuilder builder = new RequestBuilder(RequestBuilder.POST, URL.encode(url));
            final Request request = builder.sendRequest(json, new RequestCallback() {
                public void onResponseReceived(Request request, com.google.gwt.http.client.Response res) {
                    if (200 == res.getStatusCode()) {
                        // Process the response in response.getText()
                        response.handleResponseReceived(res);
                    } else {
                        // Handle the error.  Can get the status text from response.getStatusText()
                        response.handleError(new RuntimeException(url + " " + res.getStatusCode() + " (" + res.getStatusText() + ")"));
                    }
                }

                public void onError(final Request request, final Throwable exception) {
                    // Couldn't connect to server (could be timeout, SOP violation, etc.)
                    response.handleError(exception);
                }

            });
        } catch (final RequestException e) {
            // Couldn't connect to server
            response.handleError(e);
        }

        return response;
    }

    public static class ResponseImpl implements RestService.Response, HasHandlers {
        private final EventBus eventBus;

        private SuccessHandler successHandler;
        private FailureHandler failureHandler;

        private com.google.gwt.http.client.Response response;
        private Throwable exception;

        public ResponseImpl(final EventBus eventBus) {
            this.eventBus = eventBus;
        }

        public void onSuccess(final SuccessHandler handler) {
            this.successHandler = handler;
            if (response != null) {
                successHandler.handle(response.getText());
            }
        }

        public void onFailure(final FailureHandler handler) {
            this.failureHandler = handler;
            if (exception != null) {
                failureHandler.handle(exception);
            }
        }

        void handleResponseReceived(final com.google.gwt.http.client.Response response) {
            this.response = response;
            if (successHandler != null) {
                successHandler.handle(response.getText());
            }
        }

        void handleError(final Throwable exception) {
            this.exception = exception;
            if (failureHandler != null) {
                failureHandler.handle(exception);
            } else {
                AlertEvent.fireErrorFromException(this, exception.getMessage(), exception, null);
            }
        }

        @Override
        public void fireEvent(final GwtEvent<?> event) {
            eventBus.fireEvent(event);
        }
    }
}
