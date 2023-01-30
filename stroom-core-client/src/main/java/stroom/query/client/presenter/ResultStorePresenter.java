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

package stroom.query.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.query.client.presenter.ResultStorePresenter.ResultStoreView;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class ResultStorePresenter extends MyPresenterWidget<ResultStoreView> {

    private final ResultStoreListPresenter resultStoreListPresenter;

    @Inject
    public ResultStorePresenter(final EventBus eventBus,
                                final ResultStoreView view,
                                final RestFactory restFactory,
                                final ResultStoreListPresenter resultStoreListPresenter) {
        super(eventBus, view);
        this.resultStoreListPresenter = resultStoreListPresenter;

        view.setListView(resultStoreListPresenter.getView());
    }

    @Override
    protected void onBind() {
        super.onBind();
//        registerHandler(getSelectionModel().addSelectionHandler(event -> {
//            if (event.getSelectionType().isDoubleSelect()) {
//                if (getFindUserCriteria() != null &&
//                        getFindUserCriteria().getRelatedUser() == null) {
//                    HidePopupRequestEvent.builder(this).fire();
//                }
//            }
//        }));
    }

    public void show() {
        resultStoreListPresenter.refresh();

        final PopupSize popupSize = PopupSize.resizable(1000, 600);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .popupSize(popupSize)
                .caption("Search Result Stores")
//                .onShow(e -> getView().focus())
//                .onHide(e -> {
//                    if (e.isOk() && groupConsumer != null) {
//                        final User selected = getSelectionModel().getSelected();
//                        if (selected != null) {
//                            groupConsumer.accept(selected);
//                        }
//                    }
//                })
                .fire();
    }

    public interface ResultStoreView extends View {

        void setListView(final View view);
    }
}
