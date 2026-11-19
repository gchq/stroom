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

package stroom.pipeline.client.component;

import stroom.pipeline.shared.TextConverterDoc.TextConverterType;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestTextConverterSettingsController {

    @BeforeEach
    void setUp() {
        // The tab is a Composite, which can only be mocked with GWT's bridge disarmed
        GWTMockUtilities.disarm();
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void choosingATypeTellsTheEditorBeforeItIsSaved() {
        final TextConverterSettings view = Mockito.mock(TextConverterSettings.class);
        final TextConverterSettingsController controller =
                new TextConverterSettingsController(new SimpleEventBus(), view);
        final List<TextConverterType> types = new ArrayList<>();
        controller.setOnTypeChange(types::add);
        final ArgumentCaptor<Runnable> onChange = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(view).setOnChange(onChange.capture());

        // The user chooses Data Splitter. Before, the type reached the Conversion tab only when the
        // converter was read again, after a save
        Mockito.when(view.getConverterType()).thenReturn(TextConverterType.DATA_SPLITTER);
        onChange.getValue().run();

        assertThat(types).containsExactly(TextConverterType.DATA_SPLITTER);
    }
}
