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

import org.fusesource.restygwt.client.DirectRestService;
import org.fusesource.restygwt.client.MethodCallback;
import org.fusesource.restygwt.client.callback.CallbackAware;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRestFactoryImpl {

    @Test
    void call_leavesNoCallbackOnTheService() {
        // Regression test: RestyGWT leaves a call's callback on the service, which is shared (often a
        // static GWT.create), so it kept the caller, and all it reached, until the service's next
        // call: e.g. a closed query editor, kept by the query resource
        final StubService service = new StubService();
        final MethodCallback<String> callback = Mockito.mock();

        RestFactoryImpl.call(service, callback, StubService::fetch);

        // The call itself had the callback, to send the request with
        assertThat(service.callbacksAtCalls)
                .containsExactly(callback);
        // ... and the service has none once the request is sent
        assertThat(service.callback)
                .isNull();
    }

    @Test
    void call_failed_leavesNoCallbackOnTheService() {
        // As above, when making the request throws
        final StubService service = new StubService();
        service.failOnFetch = true;
        final MethodCallback<String> callback = Mockito.mock();

        assertThatThrownBy(() -> RestFactoryImpl.call(service, callback, StubService::fetch))
                .isInstanceOf(IllegalStateException.class);

        assertThat(service.callbacksAtCalls)
                .containsExactly(callback);
        assertThat(service.callback)
                .isNull();
    }


    // --------------------------------------------------------------------------------


    /// A REST service as RestyGWT generates one: the callback is set on it, and its method sends the
    /// request with it.
    private static final class StubService implements DirectRestService, CallbackAware {

        private final List<MethodCallback<?>> callbacksAtCalls = new ArrayList<>();
        private MethodCallback<?> callback;
        private boolean failOnFetch;

        @Override
        public void setCallback(final MethodCallback callback) {
            this.callback = callback;
        }

        private String fetch() {
            callbacksAtCalls.add(callback);
            if (failOnFetch) {
                throw new IllegalStateException("Failed to send");
            }
            return null;
        }
    }
}
