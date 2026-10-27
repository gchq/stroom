/*
 * Copyright 2025 Crown Copyright
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

package stroom.pathways.client.component;

import stroom.docref.DocRef;
import stroom.pathways.shared.PathwaysDoc;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// A pathways document's Pathways tab ([PathwaysSplit]): its pathways, and the paths of the one
/// selected.
public class PathwaysSplitController extends Controller {

    private final PathwayListController pathwayList;
    private PathwaysDoc document;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus    The application's event bus.
    /// @param view        The content.
    /// @param pathwayList The pathways.
    /// @param pathwayTree The paths of the pathway selected.
    @Inject
    public PathwaysSplitController(final EventBus eventBus,
                                   final PathwaysSplit view,
                                   final PathwayListController pathwayList,
                                   final PathwayTreeController pathwayTree) {
        super(eventBus);
        this.pathwayList = own(pathwayList);
        own(pathwayTree);
        initWidget(view);
        view.setPathwayList(pathwayList);
        view.setPathwayTree(pathwayTree);

        pathwayList.setOnSelectionChange(selected -> pathwayTree.read(document, selected));
    }

    /// Shows a pathways document's pathways.
    ///
    /// @param docRef   The document.
    /// @param document The document.
    public void read(final DocRef docRef, final PathwaysDoc document) {
        this.document = document;
        pathwayList.read(docRef, document);
    }
}
