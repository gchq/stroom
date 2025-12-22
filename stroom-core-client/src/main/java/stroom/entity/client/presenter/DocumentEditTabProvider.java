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

package stroom.entity.client.presenter;

import stroom.docref.DocRef;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.presenter.TabContentProvider.TabProvider;

import com.google.gwt.event.shared.GwtEvent;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HandlerContainerImpl;

import javax.inject.Provider;

public class DocumentEditTabProvider<D>
        extends HandlerContainerImpl
        implements TabProvider<D> {

    private final Provider<DocumentEditPresenter<?, D>> presenterWidgetProvider;

    private DocumentEditPresenter<?, D> widget;

    public DocumentEditTabProvider(final Provider<DocumentEditPresenter<?, D>> presenterWidgetProvider) {
        this.presenterWidgetProvider = presenterWidgetProvider;
    }

    @Override
    public DocumentEditPresenter<?, D> getPresenter() {
        if (widget == null) {
            widget = presenterWidgetProvider.get();
        }
        return widget;
    }

    @Override
    public void read(final DocRef docRef, final D document, final boolean readOnly) {
        getPresenter().read(docRef, document, readOnly);
    }

    @Override
    public D write(final D document) {
        return getPresenter().write(document);
    }

    @Override
    public void onClose() {
        getPresenter().onClose();
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return getPresenter().addDirtyHandler(handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        getPresenter().fireEvent(event);
    }
}
