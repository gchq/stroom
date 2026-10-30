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

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class RenamePopupEvent extends GwtEvent<RenamePopupEvent.Handler> {

    private static Type<Handler> TYPE;
    private final Controller controller;
    private final String caption;

    private RenamePopupEvent(final Controller controller, final String caption) {
        this.controller = controller;
        this.caption = caption;
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
        handler.onRename(this);
    }

    /// @return The controller shown as the popup.
    public Controller getController() {
        return controller;
    }

    public String getCaption() {
        return caption;
    }

    public interface Handler extends EventHandler {

        void onRename(RenamePopupEvent event);
    }

    public static class Builder {

        private final Controller controller;
        private String caption;

        public Builder(final Controller controller) {
            this.controller = controller;
        }

        public Builder caption(final String caption) {
            this.caption = caption;
            return this;
        }

        public void fire() {
            controller.fireEvent(new RenamePopupEvent(controller, caption));
        }
    }
}
