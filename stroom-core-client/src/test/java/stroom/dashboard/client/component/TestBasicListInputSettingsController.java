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

package stroom.dashboard.client.component;

import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ListInputComponentSettings;
import stroom.explorer.client.component.DocSelectionController;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.function.Consumer;

class TestBasicListInputSettingsController {

    private BasicListInputSettings view;
    private DocSelectionController dictionary;
    private BasicListInputSettingsController controller;

    @BeforeEach
    void setUp() {
        // The tab's content is a widget, which only works in a browser
        GWTMockUtilities.disarm();
        view = Mockito.mock(BasicListInputSettings.class);
        dictionary = Mockito.mock(DocSelectionController.class);
        controller = new BasicListInputSettingsController(new SimpleEventBus(), view, () -> dictionary);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void read_dictionaryEnabledByUseDictionary(final boolean useDictionary) {
        // Regression test: the Dictionary picker stayed enabled with Use Dictionary unticked
        Mockito.clearInvocations(dictionary);

        controller.read(createComponentConfig(useDictionary));

        Mockito.verify(dictionary).setEnabled(useDictionary);
        Mockito.verify(dictionary, Mockito.never()).setEnabled(!useDictionary);
    }

    @Test
    void new_dictionaryDisabled() {
        // Before any settings are read, Use Dictionary is unticked
        Mockito.verify(dictionary).setEnabled(false);
    }

    @Test
    void useDictionaryChange_enablesAndDisablesDictionary() {
        @SuppressWarnings("unchecked") final ArgumentCaptor<Consumer<Boolean>> onChange =
                ArgumentCaptor.forClass(Consumer.class);
        Mockito.verify(view).setOnUseDictionaryChange(onChange.capture());
        controller.read(createComponentConfig(false));
        Mockito.clearInvocations(dictionary);

        onChange.getValue().accept(true);
        Mockito.verify(dictionary).setEnabled(true);

        onChange.getValue().accept(false);
        Mockito.verify(dictionary).setEnabled(false);
    }

    private ComponentConfig createComponentConfig(final boolean useDictionary) {
        return ComponentConfig.builder()
                .id("component-id")
                .name("List Input")
                .settings(ListInputComponentSettings.builder()
                        .useDictionary(useDictionary)
                        .build())
                .build();
    }
}
