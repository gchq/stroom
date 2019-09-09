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

package stroom.data.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import javax.inject.Provider;

public class DataPopupSupport {
    @Inject
    public DataPopupSupport(final EventBus eventBus, final Provider<ClassificationWrappedDataPresenter> dataPresenterProvider) {
        eventBus.addHandler(ShowDataEvent.getType(), e -> {
            final ClassificationWrappedDataPresenter dataPresenter = dataPresenterProvider.get();
            dataPresenter.fetchData(e.getSourceLocation());
            final PopupSize popupSize = new PopupSize(650, 400, 650, 400, 1000, 1000, true);
            ShowPopupEvent.fire(dataPresenter, dataPresenter, PopupType.OK_CANCEL_DIALOG, popupSize, "Data", null);
        });
    }
}
