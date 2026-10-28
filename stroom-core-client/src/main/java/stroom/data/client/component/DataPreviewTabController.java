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

    private final DataPreviewTab view;

    private final DataViewerController dataPresenter;
    private DataPreviewKey dataPreviewKey;

    /// Made by GIN, with its screen and its parts.
    @Inject
    public DataPreviewTabController(final EventBus eventBus,
                                    final DataViewerController dataPresenter,
                                    final DataPreviewTab view) {
        super(eventBus);
        this.view = view;
        initWidget(view);
        own(dataPresenter);
        this.dataPresenter = dataPresenter;
        this.dataPresenter.setDisplayMode(DisplayMode.STROOM_TAB);
        view.setContentView(dataPresenter);
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
        dataPresenter.fetchData(sourceLocation);
    }

    /// @param initDataViewType How the data is first shown, e.g. as its preview or its source.
    public void setInitDataViewType(final DataViewType initDataViewType) {
        this.dataPresenter.setInitDataViewType(initDataViewType);
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
