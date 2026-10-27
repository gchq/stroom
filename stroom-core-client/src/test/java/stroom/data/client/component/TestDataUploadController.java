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

package stroom.data.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.presenter.DataTypeUiManager;
import stroom.dispatch.client.RestError;
import stroom.dispatch.client.RestErrorHandler;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.feed.client.FeedClient;
import stroom.feed.shared.FeedDoc;
import stroom.task.client.TaskMonitorFactory;
import stroom.widget.component.client.HasControllerNode;
import stroom.widget.datepicker.client.DateTimeBoxController;
import stroom.widget.form.client.FileChooserController;
import stroom.widget.popup.client.event.ShowPopupEvent;

import com.google.gwt.junit.GWTMockUtilities;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import org.fusesource.restygwt.client.Method;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

class TestDataUploadController {

    private final EventBus eventBus = new SimpleEventBus();
    private final List<AlertEvent> alerts = new ArrayList<>();
    private final List<ShowPopupEvent> shown = new ArrayList<>();

    @BeforeEach
    void setUp() {
        // The controller makes its REST resource with GWT.create, which only works in a browser
        GWTMockUtilities.disarm();
        eventBus.addHandler(AlertEvent.getType(), alerts::add);
        eventBus.addHandler(ShowPopupEvent.getType(), shown::add);
    }

    @AfterEach
    void tearDown() {
        GWTMockUtilities.restore();
    }

    @Test
    void aFeedThatCannotBeLoadedIsReportedAndNothingIsShown() {
        final DocRef feedRef = DocRef.builder().type(FeedDoc.TYPE).uuid("f1").name("FEED").build();
        final FeedClient feedClient = Mockito.mock(FeedClient.class);
        Mockito.doAnswer(invocation -> {
            final RestErrorHandler errorHandler = invocation.getArgument(2);
            errorHandler.onError(new RestError(Mockito.mock(Method.class), new RuntimeException("Not found")));
            return null;
        }).when(feedClient).load(eq(feedRef), any(), any(), any(TaskMonitorFactory.class));

        final DataUploadController controller = new DataUploadController(
                eventBus,
                Mockito.mock(DataUpload.class),
                Mockito.mock(RestFactory.class),
                feedClient,
                Mockito.mock(DataTypeUiManager.class),
                Mockito.mock(FileChooserController.class),
                () -> Mockito.mock(DateTimeBoxController.class));

        // Before, the error was reported with a callback taken from a dialog not yet shown, which
        // failed instead of saying why
        controller.show(Mockito.mock(HasControllerNode.class), feedRef, () -> {
        });

        assertThat(alerts).hasSize(1);
        assertThat(alerts.get(0).getMessage().asString()).contains("Error fetching feed").contains("Not found");
        assertThat(shown).isEmpty();
        // Never shown, so nothing else would dispose it
        assertThat(controller.isDisposed()).isTrue();
    }
}
