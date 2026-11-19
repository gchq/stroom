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

package stroom.pipeline.stepping.client.component;

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestStepLocationLinkController {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<ShowPopupEvent> shown = new ArrayList<>();
    private StepLocationLink view;
    private StepLocationPicker pickerView;
    private StepLocationLinkController controller;
    private Runnable onClick;

    @BeforeEach
    void setUp() {
        // The link and the dialog's content are GWT widgets, which can only be made in a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(ShowPopupEvent.getType(), shown::add);

        view = Mockito.mock(StepLocationLink.class);
        pickerView = Mockito.mock(StepLocationPicker.class);
        // The real dialog controller, as the popup is fired from it onto the event bus
        controller = new StepLocationLinkController(() -> new StepLocationPickerController(
                eventBus,
                pickerView))
                .attach(view);

        final ArgumentCaptor<Runnable> onClickCaptor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(view).setOnClick(onClickCaptor.capture());
        onClick = onClickCaptor.getValue();
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void click_enabled() {
        onClick.run();

        assertThat(shown)
                .extracting(ShowPopupEvent::getCaption)
                .containsExactly("Set Location");
    }

    @Test
    void ok_stepsToTheLocationEntered() {
        final List<StepLocation> steppedTo = new ArrayList<>();
        controller.setOnStepLocation(steppedTo::add);
        final StepLocation current = new StepLocation(123L, 0L, 3L);
        controller.setStepLocation(current);
        final StepLocation entered = new StepLocation(456L, 1L, 0L);
        Mockito.when(pickerView.getStepLocation()).thenReturn(entered);

        onClick.run();
        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(okPressed.isOk()).thenReturn(true);
        shown.getFirst().getHideRequestHandler().onHideRequest(okPressed);

        // The dialog starts on the location shown, and OK steps to the one entered
        Mockito.verify(pickerView).setStepLocation(current);
        assertThat(steppedTo).containsExactly(entered);
        Mockito.verify(okPressed).hide();
    }

    @Test
    void cancel_stepsNowhere() {
        final List<StepLocation> steppedTo = new ArrayList<>();
        controller.setOnStepLocation(steppedTo::add);

        onClick.run();
        final HidePopupRequestEvent cancelPressed = Mockito.mock(HidePopupRequestEvent.class);
        shown.getFirst().getHideRequestHandler().onHideRequest(cancelPressed);

        assertThat(steppedTo).isEmpty();
        Mockito.verify(cancelPressed).hide();
    }

    @Test
    void click_disabled() {
        // Regression test: Set Location could be used before a stream was chosen, sending a step
        // request with no criteria, which failed on the server.
        controller.setEnabled(false);

        onClick.run();

        assertThat(shown)
                .isEmpty();
        Mockito.verify(view).setEnabled(false);
    }

    @Test
    void click_enabledAgain() {
        controller.setEnabled(false);
        controller.setEnabled(true);

        onClick.run();

        assertThat(shown)
                .hasSize(1);
        Mockito.verify(view).setEnabled(true);
    }

    @Test
    void name_noLocation() {
        // The text is a code, so the link is named for what it does as well as what it shows
        Mockito.verify(view).setLocation("[??:??:??]", "Set Location ([??:??:??], none chosen)");
    }

    @Test
    void name_location() {
        controller.setStepLocation(new StepLocation(123L, 0L, 3L));

        Mockito.verify(view).setLocation("[123:1:4]", "Set Location ([123:1:4], stream 123, part 1, record 4)");
    }
}
