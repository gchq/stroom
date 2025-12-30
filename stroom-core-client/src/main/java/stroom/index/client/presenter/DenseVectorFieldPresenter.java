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

package stroom.index.client.presenter;

import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.index.client.presenter.DenseVectorFieldPresenter.DenseVectorFieldView;
import stroom.item.client.SelectionBox;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.DenseVectorFieldConfig.VectorSimilarityFunctionType;
import stroom.security.shared.DocumentPermission;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class DenseVectorFieldPresenter extends MyPresenterWidget<DenseVectorFieldView> {

    private final DocSelectionBoxPresenter modelPresenter;

    @Inject
    public DenseVectorFieldPresenter(final EventBus eventBus,
                                     final DenseVectorFieldView view,
                                     final DocSelectionBoxPresenter modelPresenter) {
        super(eventBus, view);
        this.modelPresenter = modelPresenter;

        modelPresenter.setIncludedTypes(OpenAIModelDoc.TYPE);
        modelPresenter.setRequiredPermissions(DocumentPermission.VIEW);
        view.setModelView(modelPresenter.getView());
    }

    public void read(final DenseVectorFieldConfig config) {
        modelPresenter.setSelectedEntityReference(config.getModelRef(), true);
        getView().getVectorSimilarityFunctionType().setValue(config.getVectorSimilarityFunction());
        getView().setSegmentSize(config.getSegmentSize());
        getView().setOverlapSize(config.getOverlapSize());
        getView().setNearestNeighbourCount(config.getNearestNeighbourCount());
    }

    public DenseVectorFieldConfig write() {
        return DenseVectorFieldConfig.builder()
                .modelRef(modelPresenter.getSelectedEntityReference())
                .vectorSimilarityFunction(getView().getVectorSimilarityFunctionType().getValue())
                .segmentSize(getView().getSegmentSize())
                .overlapSize(getView().getOverlapSize())
                .nearestNeighbourCount(getView().getNearestNeighbourCount())
                .build();
    }

    public void show(final String caption, final HidePopupRequestEvent.Handler handler) {
        final PopupSize popupSize = null;
        //PopupSize.resizable(300, 400);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption(caption)
                .onShow(e -> modelPresenter.focus())
                .onHideRequest(handler)
                .fire();
    }

    public interface DenseVectorFieldView extends View {

        void setModelView(View view);

        SelectionBox<VectorSimilarityFunctionType> getVectorSimilarityFunctionType();

        int getSegmentSize();

        void setSegmentSize(int segmentSize);

        int getOverlapSize();

        void setOverlapSize(int overlapSize);

        int getNearestNeighbourCount();

        void setNearestNeighbourCount(int nearestNeighbourCount);
    }
}
