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

package stroom.document.client;

import stroom.widget.component.client.Controller;

import com.google.gwt.user.client.ui.Widget;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestContentOpenCallback {

    @Test
    void testGivenTheScreenOpened() {
        // A tab session's next tab waits on the callback, which is given the screen (e.g. Jobs)
        final List<Controller> opened = new ArrayList<>();
        final Consumer<Controller> callback = ContentOpenCallback.of(opened::add);
        final Controller screen = new Screen();

        callback.accept(screen);

        assertThat(opened).containsExactly(screen);
    }

    @Test
    void testCalledForAScreenThatIsNotAController() {
        final List<Controller> opened = new ArrayList<>();
        final Consumer<Object> callback = ContentOpenCallback.of(opened::add);

        callback.accept(new Object());

        assertThat(opened).hasSize(1);
        assertThat(opened.get(0)).isNull();
    }

    @Test
    void testNoCallback() {
        final Consumer<Object> callback = ContentOpenCallback.of(null);

        callback.accept(new Object());
    }


    // --------------------------------------------------------------------------------


    private static final class Screen extends Controller {

        @Override
        public Widget asWidget() {
            return null;
        }
    }
}
