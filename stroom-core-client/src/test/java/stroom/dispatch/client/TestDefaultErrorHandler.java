/*
 * Copyright 2026 Crown Copyright
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

import stroom.alert.client.event.AlertCallback;
import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.CommonAlertEvent.Level;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.fusesource.restygwt.client.FailedStatusCodeException;
import org.fusesource.restygwt.client.Method;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestDefaultErrorHandler {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private final AlertCallback callback = () -> {
    };

    @BeforeEach
    void setUp() {
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
    }

    @Test
    void onError_noResponse() {
        // Regression test: a request that got no response (status code 0, no status text) was
        // reported with the exception's class name as the whole message.
        onError(new FailedStatusCodeException("", 0));

        final AlertEvent alert = alerts.getFirst();
        assertThat(alert.getLevel())
                .isEqualTo(Level.ERROR);
        assertThat(alert.getMessage().asString())
                .isEqualTo(DefaultErrorHandler.NO_RESPONSE_MESSAGE);
        // The exception is still in the details
        assertThat(alert.getDetail().asString())
                .contains(FailedStatusCodeException.class.getName());
        assertThat(alert.getCallback())
                .isSameAs(callback);
    }

    @Test
    void onError_statusText() {
        // A response with no body, e.g. from a proxy, is reported by its status text
        onError(new FailedStatusCodeException("Bad Gateway", 502));

        assertThat(alerts)
                .extracting(alert -> alert.getMessage().asString())
                .containsExactly("Bad Gateway");
    }

    @Test
    void onError_message() {
        onError(new RuntimeException("Something went wrong"));

        assertThat(alerts)
                .extracting(alert -> alert.getMessage().asString())
                .containsExactly("Something went wrong");
    }

    @Test
    void onError_noMessage() {
        // Any other exception with no message is still named by its class
        onError(new IllegalStateException());

        assertThat(alerts)
                .extracting(alert -> alert.getMessage().asString())
                .containsExactly(IllegalStateException.class.getName());
    }

    private void onError(final Throwable throwable) {
        // A method with no response, as for a request that failed
        final Method method = Mockito.mock(Method.class);
        new DefaultErrorHandler(eventBus::fireEvent, callback).onError(new RestError(method, throwable));
        assertThat(alerts)
                .hasSize(1);
    }
}
