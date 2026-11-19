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

import stroom.widget.popup.client.event.HidePopupEvent;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestAppRoot {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<Runnable> deferred = new ArrayList<>();
    private Consumer<Runnable> scheduler;

    @BeforeEach
    void setUp() {
        // JVM tests have no GWT scheduler: run what is deferred when the test says
        scheduler = ControllerDialog.deferred;
        ControllerDialog.deferred = deferred::add;
    }

    @AfterEach
    void tearDown() {
        ControllerDialog.deferred = scheduler;
    }

    @Test
    void aDialogIsOwnedByTheRootUntilHidden() {
        final AppRoot appRoot = new AppRoot();

        final Dialog dialog = appRoot.makeDialog(this::newDialog);

        assertThat(dialog.getControllerNode().parent()).isSameAs(appRoot.getControllerNode());
        HidePopupEvent.builder(dialog).fire();
        // Not until the popup manager has hidden it
        assertThat(dialog.isDisposed()).isFalse();
        deferred.forEach(Runnable::run);
        assertThat(dialog.isDisposed()).isTrue();
        assertThat(appRoot.getControllerNode().childCount()).isZero();
        assertThat(appRoot.getControllerNode().isDisposed()).isFalse();
    }

    @Test
    void aDialogLeftOpenStaysOwned() {
        final AppRoot appRoot = new AppRoot();

        appRoot.makeDialog(this::newDialog);
        appRoot.makeDialog(this::newDialog);

        assertThat(appRoot.getControllerNode().childCount()).isEqualTo(2);
    }

    @Test
    void whatItMakesIsOwnedUntilTheRootGoes() {
        final AppRoot appRoot = new AppRoot();

        final Dialog kept = appRoot.make(this::newDialog);

        assertThat(kept.getControllerNode().parent()).isSameAs(appRoot.getControllerNode());
        // Hiding it doesn't dispose it
        HidePopupEvent.builder(kept).fire();
        deferred.forEach(Runnable::run);
        assertThat(kept.isDisposed()).isFalse();
        appRoot.getControllerNode().dispose();
        assertThat(kept.isDisposed()).isTrue();
    }

    @Test
    void makingKeptFromANullProviderFails() {
        final AppRoot appRoot = new AppRoot();

        assertThatThrownBy(() -> appRoot.make(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("provider");
    }

    @Test
    void makingFromANullProviderFails() {
        final AppRoot appRoot = new AppRoot();

        assertThatThrownBy(() -> appRoot.makeDialog(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("provider");
    }

    private Dialog newDialog() {
        return new Dialog(eventBus);
    }


    // --------------------------------------------------------------------------------


    private static final class Dialog extends Controller {

        private Dialog(final EventBus eventBus) {
            super(eventBus);
        }
    }
}
