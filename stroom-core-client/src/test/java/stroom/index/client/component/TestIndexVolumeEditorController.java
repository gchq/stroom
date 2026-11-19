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

package stroom.index.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.dispatch.client.FakeRestFactory;
import stroom.index.shared.IndexVolume;
import stroom.index.shared.ValidationResult;
import stroom.node.client.NodeClient;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.task.client.TaskMonitorFactory;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestIndexVolumeEditorController {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<ConfirmEvent> confirms = new ArrayList<>();
    private final List<IndexVolume> saved = new ArrayList<>();
    private final FakeRestFactory restFactory = new FakeRestFactory();

    @BeforeEach
    void setUp() {
        // The REST resources are made with GWT.create, and the form is a widget, which only work
        // in a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(ConfirmEvent.getType(), confirms::add);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void decliningTheWarningLetsTheDialogBeUsedAgain() {
        // Regression test: declining the confirmation of a warning did nothing, so the dialog was
        // left with OK and Cancel disabled.
        final HidePopupRequestEvent okPressed = showAndPressOk();
        restFactory.getLastRequest().succeed(ValidationResult.warning("The path doesn't exist"));
        assertThat(confirms)
                .hasSize(1);

        confirms.getFirst().getCallback().onResult(false);

        Mockito.verify(okPressed).reset();
        Mockito.verify(okPressed, Mockito.never()).hide();
        // Only the validation was sent, not the volume
        assertThat(restFactory.getRequests())
                .hasSize(1);
    }

    @Test
    void acceptingTheWarningSavesTheVolume() {
        final HidePopupRequestEvent okPressed = showAndPressOk();
        restFactory.getLastRequest().succeed(ValidationResult.warning("The path doesn't exist"));

        confirms.getFirst().getCallback().onResult(true);

        assertThat(restFactory.getRequests())
                .hasSize(2);
        Mockito.verify(okPressed, Mockito.never()).reset();
    }

    // Presses OK in the dialog for a new volume.
    private HidePopupRequestEvent showAndPressOk() {
        final IndexVolumeEditor view = Mockito.mock(IndexVolumeEditor.class);
        Mockito.when(view.getByteLimit()).thenReturn("");
        Mockito.when(view.getPath()).thenReturn("/newvol");
        final IndexVolumeEditorController controller = new IndexVolumeEditorController(
                eventBus, view, restFactory, Mockito.mock(NodeClient.class));

        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(okPressed.isOk()).thenReturn(true);
        controller.onHideRequest(okPressed, IndexVolume.builder().build(), saved::add,
                Mockito.mock(TaskMonitorFactory.class));
        // The volume is validated first
        assertThat(restFactory.getRequests())
                .hasSize(1);
        return okPressed;
    }
}
