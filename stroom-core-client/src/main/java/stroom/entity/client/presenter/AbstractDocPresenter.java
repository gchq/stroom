/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.document.client.event.HasDirtyHandlers;

import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public abstract class AbstractDocPresenter<V extends View, D> extends MyPresenterWidget<V>
        implements HasDocumentRead<D>, HasDocumentWrite<D>, HasDirtyHandlers, HasClose {

    public AbstractDocPresenter(final EventBus eventBus, final V view) {
        super(eventBus, view);
    }

    public abstract boolean isDirty();

    public abstract D getEntity();

    public abstract boolean isReadOnly();
}
