/*
 * Copyright 2016 Crown Copyright
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

import stroom.core.client.LocationManager;
import stroom.data.client.ExpressionValidator;
import stroom.data.client.component.MetaList.Columns;
import stroom.dispatch.client.RestFactory;
import stroom.explorer.client.component.ExplorerPopupController;
import stroom.preferences.client.DateTimeFormatter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/// The stepping screen's streams ([MetaList], with no tick boxes), one of which is stepped through.
public class SteppingMetaListController extends MetaListController {

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                           The application's event bus.
    /// @param view                               The content.
    /// @param restFactory                        Finds the streams.
    /// @param locationManager                    Opens a download.
    /// @param dateTimeFormatter                  Formats the times shown.
    /// @param selectionSummaryControllerProvider Makes the summary of the streams chosen.
    /// @param processChoiceDialogProvider        Makes the dialog that chooses how to process them.
    /// @param pipelineSelection                  Makes the popup that picks a pipeline.
    /// @param expressionValidator                Checks an expression with the server.
    /// @param refCells                           Lets the feed and pipeline cells open them.
    @Inject
    public SteppingMetaListController(final EventBus eventBus,
                                      final MetaList view,
                                      final RestFactory restFactory,
                                      final LocationManager locationManager,
                                      final DateTimeFormatter dateTimeFormatter,
                                      final Provider<SelectionSummaryController> selectionSummaryControllerProvider,
                                      final Provider<ProcessChoiceDialogController> processChoiceDialogProvider,
                                      final Provider<ExplorerPopupController> pipelineSelection,
                                      final ExpressionValidator expressionValidator,
                                      final RefCellsController refCells) {
        super(eventBus,
                view,
                restFactory,
                locationManager,
                dateTimeFormatter,
                selectionSummaryControllerProvider,
                processChoiceDialogProvider,
                pipelineSelection,
                expressionValidator,
                refCells,
                Columns.PLAIN);
    }
}
