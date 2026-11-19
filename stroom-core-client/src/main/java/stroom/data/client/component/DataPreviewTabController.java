/*
 * Copyright 2020 Crown Copyright
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

import stroom.content.client.component.ContentTabController;
import stroom.data.client.DataPreviewKey;
import stroom.data.client.DataViewType;
import stroom.data.client.DisplayMode;
import stroom.pipeline.shared.SourceLocation;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

/// Shows a stream's data as a tab of its own.
public class DataPreviewTabController extends ContentTabController {

    public static final String TAB_TYPE = "DataPreview";

    private final DataViewerController dataController;
    private DataPreviewKey dataPreviewKey;

    /// Made by GIN, with its screen and its parts.
    ///
    /// @param eventBus       The application's event bus.
    /// @param dataController The data controller.
    @Inject
    public DataPreviewTabController(final EventBus eventBus,
                                    final DataViewerController dataController) {
        super(eventBus);
        // The tab is the viewer and nothing else, so shows the viewer's widget as its own
        this.dataController = own(dataController).attach(new DataViewer());
        this.dataController.setDisplayMode(DisplayMode.STROOM_TAB);
        initWidget(dataController.asWidget());
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.FILE_FORMATTED;
    }

    @Override
    public String getLabel() {
        return dataPreviewKey != null
                ? "Stream " + dataPreviewKey.getMetaId()
                : "Data Preview";
    }

    /// Shows a stream's data, from a place in it.
    ///
    /// @param sourceLocation The place.
    public void setSourceLocation(final SourceLocation sourceLocation) {
        dataPreviewKey = new DataPreviewKey(sourceLocation);
        dataController.fetchData(sourceLocation);
    }

    /// @param initDataViewType How the data is first shown, e.g. as its preview or its source.
    public void setInitDataViewType(final DataViewType initDataViewType) {
        this.dataController.setInitDataViewType(initDataViewType);
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
