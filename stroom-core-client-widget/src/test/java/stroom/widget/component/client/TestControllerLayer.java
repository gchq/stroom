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

package stroom.widget.component.client;

import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.Widget;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// Owners such as the table filter dialog focus their chosen tab's layer if it is a Focus, and the
// content pane focuses a tab's layer through ContentFocus, as a presenter shown as a layer was.
// Focusing a content's first tab stop needs a browser, so that path is covered by the stories
// (AppShell.OpensDocument)
class TestControllerLayer {

    @Test
    void focusingTheLayerFocusesContentThatTakesTheFocus() {
        final FocusableContent content = new FocusableContent();
        final ControllerLayer layer = new ControllerLayer(content);

        assertThat(layer).isInstanceOf(Focus.class);
        layer.focus();

        assertThat(content.focused).isEqualTo(1);
    }

    @Test
    void focusingTheLayerWithNoWidgetToFocusDoesNothing() {
        final ControllerLayer layer = new ControllerLayer(new PlainContent());

        assertThatCode(layer::focus).doesNotThrowAnyException();
    }

    // --------------------------------------------------------------------------------


    private static final class FocusableContent implements IsWidget, Focus {

        private int focused;

        @Override
        public Widget asWidget() {
            return null;
        }

        @Override
        public void focus() {
            focused++;
        }
    }

    // --------------------------------------------------------------------------------


    private static final class PlainContent implements IsWidget {

        @Override
        public Widget asWidget() {
            return null;
        }
    }
}
