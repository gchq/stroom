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

package stroom.index.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.alert.client.event.CommonAlertEvent.Level;
import stroom.index.client.presenter.IndexFieldEditPresenter.IndexFieldEditView;
import stroom.index.shared.IndexFieldImpl;
import stroom.query.api.datasource.FieldType;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class TestIndexFieldEditPresenter {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private IndexFieldEditView view;
    private IndexFieldEditPresenter presenter;

    @BeforeEach
    void setUp() {
        // The dense vector presenter's view is a GWT widget, which can only be made in a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
        view = Mockito.mock(IndexFieldEditView.class);
        presenter = new IndexFieldEditPresenter(
                eventBus,
                view,
                Mockito.mock(DenseVectorFieldPresenter.class));
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    void write_noName(final String name) {
        // Regression test: a blank name threw a ValidationException, which the New Field dialog
        // didn't catch, leaving the dialog with OK and Cancel disabled and no message.
        Mockito.when(view.getFieldName()).thenReturn(name);

        final IndexFieldImpl indexField = presenter.write();

        assertThat(indexField)
                .isNull();
        assertThat(alerts)
                .extracting(AlertEvent::getLevel, alert -> alert.getMessage().asString())
                .containsExactly(tuple(
                        Level.WARN,
                        "An index field must have a name"));
    }

    @Test
    void write_name() {
        Mockito.when(view.getFieldName()).thenReturn(" EventTime ");
        Mockito.when(view.getType()).thenReturn(FieldType.DATE);
        Mockito.when(view.isIndexed()).thenReturn(true);

        final IndexFieldImpl indexField = presenter.write();

        assertThat(indexField.getFldName())
                .isEqualTo("EventTime");
        assertThat(indexField.getFldType())
                .isEqualTo(FieldType.DATE);
        assertThat(indexField.isIndexed())
                .isTrue();
        assertThat(alerts)
                .isEmpty();
    }
}
