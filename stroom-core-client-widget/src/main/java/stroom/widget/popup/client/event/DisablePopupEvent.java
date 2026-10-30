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

package stroom.widget.popup.client.event;

import stroom.widget.component.client.Controller;
import stroom.widget.popup.client.DialogAction;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class DisablePopupEvent extends GwtEvent<DisablePopupEvent.Handler> {

    private static Type<Handler> TYPE;
    private final Controller controller;
    private final DialogAction action;

    private DisablePopupEvent(final Controller controller, final DialogAction action) {
        this.controller = controller;
        this.action = action;
    }

    public static Builder builder(final Controller controller) {
        return new Builder(controller);
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
        handler.onDisable(this);
    }

    /// @return The controller shown as the popup.
    public Controller getController() {
        return controller;
    }

    public DialogAction getAction() {
        return action;
    }

    public interface Handler extends EventHandler {

        void onDisable(DisablePopupEvent event);
    }

    public static class Builder {

        private final Controller controller;
        private DialogAction action;

        public Builder(final Controller controller) {
            this.controller = controller;
        }

        public Builder action(final DialogAction action) {
            this.action = action;
            return this;
        }

        public void fire() {
            controller.fireEvent(new DisablePopupEvent(controller, action));
        }
    }
}
