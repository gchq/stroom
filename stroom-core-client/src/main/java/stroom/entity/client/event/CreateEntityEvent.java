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

package stroom.entity.client.event;

import stroom.entity.shared.DocRef;
import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.gwtplatform.mvp.client.MyPresenter;

public class CreateEntityEvent extends GwtEvent<CreateEntityEvent.Handler> {
    public interface Handler extends EventHandler {
        void onCreate(final CreateEntityEvent event);
    }

    private static Type<Handler> TYPE;

    private final MyPresenter<?, ?> presenter;
    private final String entityType;
    private final DocRef folder;
    private final String entityName;

    private CreateEntityEvent(final MyPresenter<?, ?> presenter, final String entityType, final DocRef folder,
            final String entityName) {
        this.presenter = presenter;
        this.entityType = entityType;
        this.folder = folder;
        this.entityName = entityName;
    }

    public static void fire(final HasHandlers handlers, final MyPresenter<?, ?> presenter, final String entityType,
            final DocRef folder, final String entityName) {
        handlers.fireEvent(new CreateEntityEvent(presenter, entityType, folder, entityName));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<Handler>();
        }
        return TYPE;
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onCreate(this);
    }

    public MyPresenter<?, ?> getPresenter() {
        return presenter;
    }

    public String getEntityType() {
        return entityType;
    }

    public DocRef getFolder() {
        return folder;
    }

    public String getEntityName() {
        return entityName;
    }
}
