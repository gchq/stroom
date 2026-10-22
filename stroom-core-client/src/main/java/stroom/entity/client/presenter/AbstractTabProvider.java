/*
 * Copyright 2023 Crown Copyright
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
import stroom.entity.client.presenter.TabContentProvider.TabProvider;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.gwtplatform.mvp.client.HandlerContainerImpl;

public abstract class AbstractTabProvider<E, P extends MyPresenterWidget<?>>
        extends HandlerContainerImpl
        implements TabProvider<E> {

    private P presenterWidget;

    public AbstractTabProvider() {
        // A tab's presenter tells its document of changes itself (Contexts.DIRTY)
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
    public final void read(final DocRef docRef, final E document) {
        onRead(getPresenter(), docRef, document);
    }

    @Override
    public final E write(final E document) {
        return onWrite(getPresenter(), document);
    }

    public void onRead(final P presenter, final DocRef docRef, final E document) {
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

}
