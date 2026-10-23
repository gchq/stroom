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

import stroom.widget.component.client.ComponentDialog.DialogHost;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Showing and hiding need a browser (the popup manager), so these tests cover what is shown and
// who owns it; disposal once hidden is tested with Component.makeDialog (TestComponent)
class TestComponentDialog {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<ShowPopupEvent> shown = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ComponentChecks.setEnabled(true);
        ComponentChecks.reset();
        eventBus.addHandler(ShowPopupEvent.getType(), shown::add);
    }

    @AfterEach
    void tearDown() {
        ComponentChecks.reset();
    }

    @Test
    void theDialogIsShownThroughAPresenterThatOwnsIt() {
        final ComponentNode opener = ComponentNode.root("opener");
        final Editor editor = new Editor(eventBus);

        ComponentDialog.showOnce(() -> opener, editor)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption("Edit")
                .fire();

        assertThat(shown).hasSize(1);
        final ShowPopupEvent event = shown.get(0);
        assertThat(event.getCaption()).isEqualTo("Edit");
        assertThat(event.getPresenterWidget()).isInstanceOfSatisfying(DialogHost.class, host -> {
            assertThat(host.getContent()).isSameAs(editor);
            assertThat(host.getComponentNode().parent()).isSameAs(opener);
            assertThat(editor.getComponentNode().parent()).isSameAs(host.getComponentNode());
        });
        assertThat(ComponentChecks.unownedNames()).isEmpty();
    }

    @Test
    void theDialogSeesItsOpenersContext() {
        final ComponentNode opener = ComponentNode.root("opener");
        opener.provide(Contexts.READ_ONLY, ContextValue.of(false));
        final Editor editor = new Editor(eventBus);

        ComponentDialog.showOnce(() -> opener, editor).fire();

        assertThat(editor.readOnly).containsExactly(false);
    }

    @Test
    void theDialogIsDisposedWithItsOpener() {
        final ComponentNode opener = ComponentNode.root("opener");
        final Editor editor = new Editor(eventBus);
        ComponentDialog.showOnce(() -> opener, editor).fire();

        opener.dispose();

        assertThat(editor.isDisposed()).isTrue();
    }

    @Test
    void aComponentWithoutAnEventBusCantBeADialog() {
        final ComponentNode opener = ComponentNode.root("opener");
        final Editor editor = new Editor(null);

        assertThatThrownBy(() -> ComponentDialog.showOnce(() -> opener, editor))
                .hasMessageContaining("Editor can't be shown as a dialog");
        assertThat(opener.isDisposed()).isFalse();
    }


    // --------------------------------------------------------------------------------


    private static final class Editor extends Component {

        private final List<Boolean> readOnly = new ArrayList<>();

        private Editor(final EventBus eventBus) {
            super(eventBus);
            watch(Contexts.READ_ONLY, readOnly::add);
        }
    }
}
