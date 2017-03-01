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

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.gwtplatform.mvp.client.PresenterWidget;

import stroom.entity.client.EntityTabData;
import stroom.entity.shared.PermissionInheritance;

public class SaveAsEntityEvent extends GwtEvent<SaveAsEntityEvent.Handler> {
    public interface Handler extends EventHandler {
        void onSaveAs(final SaveAsEntityEvent event);
    }

    private static Type<Handler> TYPE;

    private final PresenterWidget<?> dialog;
    private final EntityTabData tabData;
    private final String entityName;
    private final PermissionInheritance permissionInheritance;

    private SaveAsEntityEvent(final PresenterWidget<?> dialog, final EntityTabData tabData, final String entityName, final PermissionInheritance permissionInheritance) {
        this.dialog = dialog;
        this.tabData = tabData;
        this.entityName = entityName;
        this.permissionInheritance = permissionInheritance;
    }

    public static void fire(final HasHandlers handlers, final PresenterWidget<?> dialog, final EntityTabData tabData,
            final String entityName, final PermissionInheritance permissionInheritance) {
        handlers.fireEvent(new SaveAsEntityEvent(dialog, tabData, entityName, permissionInheritance));
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
        handler.onSaveAs(this);
    }

    public PresenterWidget<?> getDialog() {
        return dialog;
    }

    public EntityTabData getTabData() {
        return tabData;
    }

    public String getEntityName() {
        return entityName;
    }

    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance;
    }
}
