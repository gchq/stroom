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

public class HidePopupEvent extends GwtEvent<HidePopupEvent.Handler> {

    private static Type<Handler> TYPE;
    private final Controller controller;
    private final boolean autoClose;
    private final boolean ok;

    private HidePopupEvent(final Controller controller, final boolean autoClose, final boolean ok) {
        this.controller = controller;
        this.autoClose = autoClose;
        this.ok = ok;
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
        handler.onHide(this);
    }

    /// @return The controller shown as the popup.
    public Controller getController() {
        return controller;
    }

    public boolean isAutoClose() {
        return autoClose;
    }

    public boolean isOk() {
        return ok;
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onHide(HidePopupEvent event);
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private final Controller controller;
        private boolean autoClose;
        private boolean ok = true;

        public Builder(final Controller controller) {
            this.controller = controller;
        }

        public Builder autoClose(final boolean autoClose) {
            this.autoClose = autoClose;
            return this;
        }

        public Builder ok(final boolean ok) {
            this.ok = ok;
            return this;
        }

        public HidePopupEvent build() {
            return new HidePopupEvent(
                    controller,
                    autoClose,
                    ok);
        }

        public void fire() {
            controller.fireEvent(build());
        }
    }
}
