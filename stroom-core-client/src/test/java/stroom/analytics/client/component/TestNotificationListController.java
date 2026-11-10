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

package stroom.analytics.client.component;

import stroom.analytics.shared.NotificationConfig;
import stroom.docref.DocRef;
import stroom.widget.util.client.MultiSelectionModelImpl;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;

class TestNotificationListController {

    private final MultiSelectionModelImpl<NotificationConfig> selectionModel = new MultiSelectionModelImpl<>();
    private AnalyticNotificationEditorController editor;
    private NotificationListController controller;
    private Runnable onEdit;

    @BeforeEach
    void setUp() {
        GWTMockUtilities.disarm();
        final NotificationList view = Mockito.mock(NotificationList.class);
        Mockito.when(view.getSelectionModel()).thenReturn(selectionModel);
        editor = Mockito.mock(AnalyticNotificationEditorController.class);
        controller = new NotificationListController(new SimpleEventBus(), view, () -> editor);
        final ArgumentCaptor<Runnable> edit = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(view).setOnEdit(edit.capture());
        onEdit = edit.getValue();
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void editingTheSameNotificationTwiceKeepsOneRow() {
        final NotificationConfig first = notification("a", true);
        final NotificationConfig other = notification("b", true);
        controller.read(new DocRef("AnalyticRule", "rule"), List.of(first, other));
        selectionModel.setSelected(first);

        editAndPressOk(edited -> edited.copy().enabled(false).build());
        // The selection is left as it was, the object before the edit, as a list without a grid
        // to follow its rows would leave it. Before, this second edit added a second row.
        editAndPressOk(edited -> edited.copy().limitNotifications(true).build());

        final List<NotificationConfig> notifications = controller.getNotifications();
        assertThat(notifications).extracting(NotificationConfig::getUuid).containsExactly("a", "b");
        assertThat(notifications.get(0).isLimitNotifications()).isTrue();
    }

    @Test
    void anEditOfANotificationRemovedMeanwhileIsNotAdded() {
        final NotificationConfig first = notification("a", true);
        controller.read(new DocRef("AnalyticRule", "rule"), List.of(first));
        selectionModel.setSelected(first);
        onEdit.run();
        final Consumer<NotificationConfig> onOk = captureOnOk();

        // The document is read again (e.g. reverted) without it while the dialog is open
        controller.read(new DocRef("AnalyticRule", "rule"), List.of());
        onOk.accept(first.copy().enabled(false).build());

        assertThat(controller.getNotifications()).isEmpty();
    }

    // Opens the editor on the selected notification and presses OK with the change given
    private void editAndPressOk(final UnaryOperator<NotificationConfig> change) {
        Mockito.clearInvocations(editor);
        onEdit.run();
        final ArgumentCaptor<NotificationConfig> config = ArgumentCaptor.forClass(NotificationConfig.class);
        @SuppressWarnings("unchecked") final ArgumentCaptor<Consumer<NotificationConfig>> onOk =
                ArgumentCaptor.forClass(Consumer.class);
        Mockito.verify(editor).edit(any(), any(), any(), config.capture(), anyString(), onOk.capture());
        onOk.getValue().accept(change.apply(config.getValue()));
    }

    @SuppressWarnings("unchecked")
    private Consumer<NotificationConfig> captureOnOk() {
        final ArgumentCaptor<Consumer<NotificationConfig>> onOk = ArgumentCaptor.forClass(Consumer.class);
        Mockito.verify(editor).edit(any(), any(), any(), any(), anyString(), onOk.capture());
        return onOk.getValue();
    }

    private static NotificationConfig notification(final String uuid, final boolean enabled) {
        return NotificationConfig.builder().uuid(uuid).enabled(enabled).build();
    }
}
