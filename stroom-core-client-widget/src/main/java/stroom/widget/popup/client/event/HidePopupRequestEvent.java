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
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

public class HidePopupRequestEvent extends GwtEvent<HidePopupRequestEvent.Handler> {

    private static Type<Handler> TYPE;
    private final Controller controller;
    private final boolean autoClose;
    private final DialogAction action;
    private final Runnable resetHandler;

    private HidePopupRequestEvent(final Controller controller,
                                  final boolean autoClose,
                                  final DialogAction action,
                                  final Runnable resetHandler) {
        this.controller = controller;
        this.autoClose = autoClose;
        this.action = action;
        this.resetHandler = resetHandler;
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
        handler.onHideRequest(this);
    }

    /// @return The controller shown as the popup.
    public Controller getController() {
        return controller;
    }

    public boolean isAutoClose() {
        return autoClose;
    }

    public boolean isOk() {
        return action == DialogAction.OK;
    }

    public boolean isCreate() {
        return action == DialogAction.CREATE;
    }

    /**
     * Call to actually hide the popup if the request is handled and accepted.
     */
    public void hide() {
        HidePopupEvent.builder(controller).autoClose(autoClose).ok(isOk()).fire();
    }

    /**
     * Call to reset/cancel the request and re-enable any buttons.
     */
    public void reset() {
        if (resetHandler != null) {
            resetHandler.run();
        }
    }


    // --------------------------------------------------------------------------------


    public interface Handler extends EventHandler {

        void onHideRequest(HidePopupRequestEvent event);
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private final Controller controller;

        private boolean autoClose;
        private DialogAction action = DialogAction.OK;
        private Runnable cancelHandler;

        public Builder(final Controller controller) {
            this.controller = controller;
        }

        public Builder autoClose(final boolean autoClose) {
            this.autoClose = autoClose;
            return this;
        }

        public Builder action(final DialogAction action) {
            this.action = action;
            return this;
        }

        public Builder cancelHandler(final Runnable cancelHandler) {
            this.cancelHandler = cancelHandler;
            return this;
        }

        public void fire() {
            controller.fireEvent(new HidePopupRequestEvent(
                    controller,
                    autoClose,
                    action,
                    cancelHandler));
        }
    }
}
