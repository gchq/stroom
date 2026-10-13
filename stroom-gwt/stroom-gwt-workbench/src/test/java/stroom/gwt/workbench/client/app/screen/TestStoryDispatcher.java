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

package stroom.gwt.workbench.client.app.screen;

import com.google.gwt.http.client.Request;
import com.google.gwt.http.client.RequestBuilder;
import com.google.gwt.http.client.RequestException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStoryDispatcher {

    @Test
    void testSendsToTheDelegate() throws RequestException {
        final List<RequestBuilder> sent = new ArrayList<>();
        final StoryDispatcher dispatcher = new StoryDispatcher();
        dispatcher.setDelegate((method, builder) -> {
            sent.add(builder);
            return null;
        });

        final RequestBuilder builder = new RequestBuilder(RequestBuilder.GET, "http://localhost/api/a");
        final Request request = dispatcher.send(null, builder);

        assertThat(request).isNull();
        assertThat(sent).containsExactly(builder);
    }

    @Test
    void testSendsToTheLatestDelegate() throws RequestException {
        final List<String> sentTo = new ArrayList<>();
        final StoryDispatcher dispatcher = new StoryDispatcher();
        dispatcher.setDelegate((method, builder) -> {
            sentTo.add("first");
            return null;
        });
        dispatcher.setDelegate((method, builder) -> {
            sentTo.add("second");
            return null;
        });

        dispatcher.send(null, new RequestBuilder(RequestBuilder.GET, "http://localhost/api/a"));

        assertThat(sentTo).containsExactly("second");
    }

    @Test
    void testRefusesRequestsWithoutADelegate() {
        final StoryDispatcher dispatcher = new StoryDispatcher();

        assertThatThrownBy(() -> dispatcher.send(null,
                new RequestBuilder(RequestBuilder.POST, "http://localhost/api/b")))
                .isInstanceOf(RequestException.class)
                .hasMessageContaining("POST http://localhost/api/b");
    }

    @Test
    void testHasDelegateOnceSet() {
        final StoryDispatcher dispatcher = new StoryDispatcher();
        assertThat(dispatcher.hasDelegate()).isFalse();

        dispatcher.setDelegate((method, builder) -> null);

        assertThat(dispatcher.hasDelegate()).isTrue();
    }

    @Test
    void testRefusesANullDelegate() {
        final StoryDispatcher dispatcher = new StoryDispatcher();

        assertThatThrownBy(() -> dispatcher.setDelegate(null))
                .isInstanceOf(NullPointerException.class);
    }
}
