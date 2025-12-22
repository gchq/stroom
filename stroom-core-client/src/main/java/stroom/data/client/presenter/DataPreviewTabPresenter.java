/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.data.client.presenter;

import stroom.content.client.presenter.ContentTabPresenter;
import stroom.data.client.DataPreviewKey;
import stroom.data.client.presenter.DataPreviewTabPresenter.DataPreviewTabView;
import stroom.pipeline.shared.SourceLocation;
import stroom.svg.shared.SvgImage;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

public class DataPreviewTabPresenter extends ContentTabPresenter<DataPreviewTabView> {

    public static final String TAB_TYPE = "DataPreview";
    private final DataPresenter dataPresenter;
    private DataPreviewKey dataPreviewKey;

    @Inject
    public DataPreviewTabPresenter(final EventBus eventBus,
                                   final DataPresenter dataPresenter,
                                   final DataPreviewTabView view) {
        super(eventBus, view);
        this.dataPresenter = dataPresenter;
        this.dataPresenter.setDisplayMode(DisplayMode.STROOM_TAB);
        getView().setContentView(dataPresenter.getView());
    }

    @Override
    protected void onBind() {
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

    public void setSourceLocation(final SourceLocation sourceLocation) {
        dataPreviewKey = new DataPreviewKey(sourceLocation);
        dataPresenter.fetchData(sourceLocation);
    }

    public void setInitDataViewType(final DataViewType initDataViewType) {
        this.dataPresenter.setInitDataViewType(initDataViewType);
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }


    // --------------------------------------------------------------------------------


    public interface DataPreviewTabView extends View {

        void setContentView(final View sourceView);
    }
}
