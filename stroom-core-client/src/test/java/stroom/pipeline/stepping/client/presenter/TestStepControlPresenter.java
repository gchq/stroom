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

import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.presenter.StepControlPresenter.StepControlView;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestStepControlPresenter {

    private StepControlView view;
    private StepControlPresenter presenter;

    @BeforeEach
    void setUp() {
        GWTMockUtilities.disarm();
        view = Mockito.mock(StepControlView.class);
        presenter = new StepControlPresenter(new SimpleEventBus(), view);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void initButtons() {
        // Regression test: refresh started enabled, so it could be pressed before a stream was
        // chosen, sending a step request with no criteria, which failed on the server.
        presenter.initButtons();

        Mockito.verify(view).setStepFirstEnabled(false);
        Mockito.verify(view).setStepBackwardEnabled(false);
        Mockito.verify(view).setStepForwardEnabled(false);
        Mockito.verify(view).setStepLastEnabled(false);
        Mockito.verify(view).setStepRefreshEnabled(false);
        Mockito.verify(view, Mockito.never()).setStepRefreshEnabled(true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void setRefreshEnabled(final boolean enabled) {
        presenter.setRefreshEnabled(enabled);

        Mockito.verify(view).setStepRefreshEnabled(enabled);
    }

    @Test
    void setEnabledButtons_leavesRefreshAlone() {
        // Refresh is only enabled once a stream is chosen, so a step result mustn't change it.
        presenter.initButtons();
        Mockito.clearInvocations(view);

        presenter.setEnabledButtons(StepType.REFRESH, true, false, null, true, true);

        Mockito.verify(view, Mockito.never()).setStepRefreshEnabled(Mockito.anyBoolean());
    }

    @Test
    void stepRefresh() {
        final List<StepType> steps = new ArrayList<>();
        presenter.addStepControlHandler(event -> steps.add(event.getStepType()));

        presenter.step(StepType.REFRESH);

        assertThat(steps)
                .containsExactly(StepType.REFRESH);
    }
}
