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

import stroom.pipeline.shared.stepping.StepType;

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

class TestStepControlController {

    private StepControl view;
    private StepControlController controller;

    @BeforeEach
    void setUp() {
        GWTMockUtilities.disarm();
        view = Mockito.mock(StepControl.class);
        controller = new StepControlController(new SimpleEventBus()).attach(view);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void initButtons() {
        // Regression test: refresh started enabled, so it could be pressed before a stream was
        // chosen, sending a step request with no criteria, which failed on the server.
        controller.initButtons();

        Mockito.verify(view).setStepEnabled(StepType.FIRST, false);
        Mockito.verify(view).setStepEnabled(StepType.BACKWARD, false);
        Mockito.verify(view).setStepEnabled(StepType.FORWARD, false);
        Mockito.verify(view).setStepEnabled(StepType.LAST, false);
        Mockito.verify(view).setStepEnabled(StepType.REFRESH, false);
        Mockito.verify(view, Mockito.never()).setStepEnabled(StepType.REFRESH, true);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void setRefreshEnabled(final boolean enabled) {
        controller.setRefreshEnabled(enabled);

        Mockito.verify(view).setStepEnabled(StepType.REFRESH, enabled);
    }

    @Test
    void setEnabledButtons_leavesRefreshAlone() {
        // Refresh is only enabled once a stream is chosen, so a step result mustn't change it.
        controller.initButtons();
        Mockito.clearInvocations(view);

        controller.setEnabledButtons(StepType.REFRESH, true, false, null, true, true);

        Mockito.verify(view, Mockito.never()).setStepEnabled(Mockito.eq(StepType.REFRESH), Mockito.anyBoolean());
    }

    @Test
    void stepRefresh() {
        final List<StepType> steps = new ArrayList<>();
        controller.setOnStep(steps::add);

        controller.step(StepType.REFRESH);

        assertThat(steps)
                .containsExactly(StepType.REFRESH);
    }
}
