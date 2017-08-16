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

package stroom.entity.client.presenter;

import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.entity.client.event.DirtyEvent;
import stroom.entity.client.event.DirtyEvent.DirtyHandler;
import stroom.entity.client.event.HasDirtyHandlers;
import stroom.entity.client.event.ReloadEntityEvent;
import stroom.entity.shared.Document;
import stroom.entity.shared.NamedEntity;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.HasReadOnly;
import stroom.util.shared.HasType;
import stroom.widget.tickbox.client.view.TickBox;
import stroom.widget.valuespinner.client.ValueSpinner;

public abstract class EntityEditPresenter<V extends View, E extends NamedEntity> extends MyPresenterWidget<V>
        implements HasRead<E>, HasWrite<E>, HasPermissionCheck, HasDirtyHandlers, HasType {
    private final ClientSecurityContext securityContext;
    private E entity;
    private boolean dirty;
    private boolean reading;
    private boolean checkedPermissions;
    private boolean readOnly = true;

    public EntityEditPresenter(final EventBus eventBus, final V view, final ClientSecurityContext securityContext) {
        super(eventBus, view);
        this.securityContext = securityContext;
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getEventBus().addHandler(ReloadEntityEvent.getType(), event -> {
            if (entity.equals(event.getEntity())) {
                read((E) event.getEntity());
            }
        }));
    }

    private void setDirty(final boolean dirty, final boolean force) {
        if (!isReadOnly()) {
            if (!reading && (force || this.dirty != dirty)) {
                this.dirty = dirty;
                DirtyEvent.fire(this, dirty);
                onDirty(dirty);
            }
        }
    }

    public void onDirty(final boolean dirty) {
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(final boolean dirty) {
        setDirty(dirty, false);
    }

    @Override
    public final void read(final E entity) {
        // Check document permissions if we haven't already.
        if (!checkedPermissions) {
            checkedPermissions = true;
            if (entity instanceof Document) {
                final Document document = (Document) entity;
                securityContext.hasDocumentPermission(document.getType(), document.getUuid(), DocumentPermissionNames.UPDATE).onSuccess(this::setAllowUpdate);
            } else {
                onPermissionsCheck(false);
            }
        }

        this.entity = entity;
        if (entity != null) {
            reading = true;
            onRead(entity);
            reading = false;
            setDirty(false, true);
        }
    }

    @Override
    public final void write(final E entity) {
        onWrite(entity);
    }

    @Override
    public void onPermissionsCheck(boolean readOnly) {
        this.readOnly = readOnly;
        if (getView() instanceof HasReadOnly) {
            final HasReadOnly hasReadOnly = (HasReadOnly) getView();
            hasReadOnly.setReadOnly(readOnly);
        }
    }

    private void setAllowUpdate(Boolean allowUpdate) {
        onPermissionsCheck(!allowUpdate);
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    /**
     * Called when an attempt is made to close this presenter
     */
    public void onClose() {
    }

    public E getEntity() {
        return entity;
    }

    protected abstract void onRead(E entity);

    protected abstract void onWrite(E entity);

    protected HandlerRegistration addDirtyHandler(final TickBox tickBox) {
        return tickBox.addValueChangeHandler(event -> setDirty(true));
    }

    protected HandlerRegistration addDirtyHandler(final ValueSpinner spinner) {
        return spinner.getSpinner().addSpinnerHandler(event -> setDirty(true));
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }
}
