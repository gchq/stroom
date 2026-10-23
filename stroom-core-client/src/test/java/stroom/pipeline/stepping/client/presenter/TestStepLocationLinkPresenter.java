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

package stroom.pipeline.stepping.client.presenter;

import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.client.presenter.StepLocationLinkPresenter.StepLocationLinkView;
import stroom.pipeline.stepping.client.presenter.StepLocationPresenter.StepLocationView;
import stroom.widget.button.client.LinkButton;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.event.dom.client.ClickHandler;
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

class TestStepLocationLinkPresenter {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<ShowPopupEvent> shown = new ArrayList<>();
    private StepLocationLinkView view;
    private StepLocationLinkPresenter presenter;
    private ClickHandler clickHandler;
    private LinkButton button;

    @BeforeEach
    void setUp() {
        // The button is a GWT widget, which can only be made in a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(ShowPopupEvent.getType(), shown::add);

        button = Mockito.mock(LinkButton.class);
        view = Mockito.mock(StepLocationLinkView.class);
        Mockito.when(view.getButton()).thenReturn(button);

        // The real presenter, as the popup is fired from it onto the event bus
        final StepLocationPresenter stepLocationPresenter = new StepLocationPresenter(
                eventBus,
                Mockito.mock(StepLocationView.class));
        presenter = new StepLocationLinkPresenter(eventBus, view, stepLocationPresenter);

        final ArgumentCaptor<ClickHandler> clickHandlerCaptor = ArgumentCaptor.forClass(ClickHandler.class);
        Mockito.verify(button).addClickHandler(clickHandlerCaptor.capture());
        clickHandler = clickHandlerCaptor.getValue();
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void click_enabled() {
        clickHandler.onClick(null);

        assertThat(shown)
                .extracting(ShowPopupEvent::getCaption)
                .containsExactly("Set Location");
    }

    @Test
    void click_disabled() {
        // Regression test: Set Location could be used before a stream was chosen, sending a step
        // request with no criteria, which failed on the server.
        presenter.setEnabled(false);

        clickHandler.onClick(null);

        assertThat(shown)
                .isEmpty();
        Mockito.verify(view).setEnabled(false);
    }

    @Test
    void click_enabledAgain() {
        presenter.setEnabled(false);
        presenter.setEnabled(true);

        clickHandler.onClick(null);

        assertThat(shown)
                .hasSize(1);
        Mockito.verify(view).setEnabled(true);
    }

    @Test
    void name_noLocation() {
        // The text is a code, so the button is named for what it does as well as what it shows
        Mockito.verify(button).setText("[??:??:??]");
        Mockito.verify(button).setAriaLabel("Set Location ([??:??:??], none chosen)");
    }

    @Test
    void name_location() {
        presenter.setStepLocation(new StepLocation(123L, 0L, 3L));

        Mockito.verify(button).setText("[123:1:4]");
        Mockito.verify(button).setAriaLabel("Set Location ([123:1:4], stream 123, part 1, record 4)");
    }
}
