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
import stroom.explorer.shared.ExplorerData;
import stroom.entity.shared.PermissionInheritance;

import java.util.List;

public class MoveEntityEvent extends GwtEvent<MoveEntityEvent.Handler> {
    private static Type<Handler> TYPE;
    private final PresenterWidget<?> presenter;
    private final ExplorerData folder;
    private final List<ExplorerData> children;
    private final PermissionInheritance permissionInheritance;

    private MoveEntityEvent(final PresenterWidget<?> presenter,
                            final ExplorerData folder, final List<ExplorerData> children, final PermissionInheritance permissionInheritance) {
        this.presenter = presenter;
        this.folder = folder;
        this.children = children;
        this.permissionInheritance = permissionInheritance;
    }

    public static void fire(final HasHandlers handlers, final PresenterWidget<?> presenter,
                            final ExplorerData folder, final List<ExplorerData> children, final PermissionInheritance permissionInheritance) {
        handlers.fireEvent(new MoveEntityEvent(presenter, folder, children, permissionInheritance));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public final Type<Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onMove(this);
    }

    public PresenterWidget<?> getPresenter() {
        return presenter;
    }

    public ExplorerData getFolder() {
        return folder;
    }

    public List<ExplorerData> getChildren() {
        return children;
    }

    public PermissionInheritance getPermissionInheritance() {
        return permissionInheritance;
    }

    public interface Handler extends EventHandler {
        void onMove(final MoveEntityEvent event);
    }
}
