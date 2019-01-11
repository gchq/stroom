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

package stroom.iframe.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;

public class IFramePresenter extends MyPresenterWidget<IFramePresenter.IFrameView> implements IFrameLoadUiHandlers, HasDirtyHandlers {
    @Inject
    public IFramePresenter(final EventBus eventBus, final IFrameView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    public void setUrl(final String url) {
        getView().setUrl(url);
    }

    public void setCustomTitle(final String customTitle) {
        getView().setCustomTitle(customTitle);
    }

    public String getLabel() {
        return getView().getTitle();
    }

    public void close() {
        getView().cleanup();
    }

    @Override
    public void onTitleChange(final String title) {
        DirtyEvent.fire(this, true);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public interface IFrameView extends View, HasUiHandlers<IFrameLoadUiHandlers> {
        void setUrl(String url);

        void setCustomTitle(String customTitle);

        String getTitle();

        void cleanup();
    }
}