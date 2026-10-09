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

import stroom.pipeline.stepping.client.presenter.StepLocationLinkPresenter.StepLocationLinkView;
import stroom.pipeline.stepping.client.presenter.StepLocationPresenter.StepLocationView;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.junit.GWTMockUtilities;
import com.google.gwt.user.client.ui.Label;
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

    @BeforeEach
    void setUp() {
        // The label is a GWT widget, which can only be made in a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(ShowPopupEvent.getType(), shown::add);

        final Label label = Mockito.mock(Label.class);
        view = Mockito.mock(StepLocationLinkView.class);
        Mockito.when(view.getLabel()).thenReturn(label);

        // The real presenter, as the popup is fired from it onto the event bus
        final StepLocationPresenter stepLocationPresenter = new StepLocationPresenter(
                eventBus,
                Mockito.mock(StepLocationView.class));
        presenter = new StepLocationLinkPresenter(eventBus, view, stepLocationPresenter);
        presenter.bind();

        final ArgumentCaptor<ClickHandler> clickHandlerCaptor = ArgumentCaptor.forClass(ClickHandler.class);
        Mockito.verify(label).addClickHandler(clickHandlerCaptor.capture());
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
}
