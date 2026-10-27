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

package stroom.annotation.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAnnotationTagEditController {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<AlertEvent> alerts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        GWTMockUtilities.disarm();
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @ParameterizedTest
    @CsvSource({
            "STATUS, status",
            "LABEL, label",
            "COLLECTION, collection",
            "COMMENT, comment"})
    @SuppressWarnings("unchecked")
    void blankNameNamesTheTagType(final AnnotationTagType type, final String typeName) {
        // Regression test: the error always called the tag an "annotation group", whatever its type
        // The form is a widget, which can only be made in a browser
        final AnnotationTagEdit view = Mockito.mock(AnnotationTagEdit.class);
        Mockito.when(view.getName()).thenReturn("");
        final AnnotationTagEditController controller = new AnnotationTagEditController(
                eventBus,
                view,
                Mockito.mock(AnnotationResourceClient.class),
                Mockito.mock(Provider.class));

        controller.read(AnnotationTag.builder().type(type).name("Old").build());
        final HidePopupRequestEvent okPressed = Mockito.mock(HidePopupRequestEvent.class);
        Mockito.when(okPressed.isOk()).thenReturn(true);
        controller.onHideRequest(okPressed, tag -> {
        });

        assertThat(alerts)
                .extracting(alert -> alert.getMessage().asString())
                .containsExactly("You must provide a name for the annotation " + typeName + ".");
    }
}
