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
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.presenter.TabContentProvider.TabProvider;

import com.google.gwt.event.shared.GwtEvent;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.HandlerContainerImpl;
import com.gwtplatform.mvp.client.MyPresenterWidget;

public abstract class AbstractTabProvider<E, P extends MyPresenterWidget<?>>
        extends HandlerContainerImpl
        implements TabProvider<E> {

    private final EventBus eventBus;

    private P presenterWidget;

    public AbstractTabProvider(final EventBus eventBus) {
        this.eventBus = eventBus;
    }

    protected abstract P createPresenter();

    @Override
    public final P getPresenter() {
        if (presenterWidget == null) {
            presenterWidget = createPresenter();
        }
        return presenterWidget;
    }

    @Override
    public final void read(final DocRef docRef, final E document, final boolean readOnly) {
        onRead(getPresenter(), docRef, document, readOnly);
    }

    @Override
    public final E write(final E document) {
        return onWrite(getPresenter(), document);
    }

    public void onRead(final P presenter, final DocRef docRef, final E document, final boolean readOnly) {
    }

    public E onWrite(final P presenter, final E document) {
        return document;
    }

    @Override
    public void onClose() {
        if (presenterWidget instanceof HasClose) {
            ((HasClose) presenterWidget).onClose();
        }
    }

    protected void fireDirtyEvent(final boolean dirty) {
        DirtyEvent.fire(this, dirty);
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return eventBus.addHandlerToSource(DirtyEvent.getType(), this, handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEventFromSource(event, this);
    }
}
