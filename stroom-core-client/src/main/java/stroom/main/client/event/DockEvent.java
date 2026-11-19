/*
 * Copyright 2025 Crown Copyright
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

package stroom.main.client.event;

import stroom.ai.client.component.AskStroomAiController.DockBehaviour;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.Size;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class DockEvent extends GwtEvent<DockEvent.Handler> {

    private static Type<Handler> TYPE;

    private final Controller controller;
    private final DockBehaviour dockBehaviour;
    private final Size size;
    private final DockAction action;

    private DockEvent(final Controller controller,
                      final DockBehaviour dockBehaviour,
                      final Size size,
                      final DockAction action) {
        this.controller = controller;
        this.dockBehaviour = dockBehaviour;
        this.size = size;
        this.action = action;
    }

    public static void fire(final HasHandlers handlers,
                            final Controller controller,
                            final DockBehaviour dockBehaviour,
                            final Size size) {
        handlers.fireEvent(new DockEvent(controller, dockBehaviour, size, DockAction.DOCK));
    }

    public static void fireUndock(final HasHandlers handlers,
                                  final Controller controller) {
        handlers.fireEvent(new DockEvent(controller, null, null, DockAction.UNDOCK));
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onDock(this);
    }

    public Controller getController() {
        return controller;
    }

    public DockBehaviour getDockBehaviour() {
        return dockBehaviour;
    }

    public Size getSize() {
        return size;
    }

    public DockAction getAction() {
        return action;
    }

    public enum DockAction {
        DOCK,
        UNDOCK
    }

    public interface Handler extends EventHandler {

        void onDock(DockEvent event);
    }
}
