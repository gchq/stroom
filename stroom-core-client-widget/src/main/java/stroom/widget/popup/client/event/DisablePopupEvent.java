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

import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.gwtplatform.mvp.client.PresenterWidget;

// STROOMWORKS-LOCAL: KEEP LOCAL ON MERGE FROM master.
// Part of "Dialog box validation now supports disabling only the OK button" - a local addition
// upstream does not have. FloorMap's edit dialogs rely on it, so keep this side on merge.
public class DisablePopupEvent extends GwtEvent<DisablePopupEvent.Handler> {

    private static Type<Handler> TYPE;
    private final PresenterWidget<?> presenterWidget;
    private final DialogAction action;

    private DisablePopupEvent(final PresenterWidget<?> presenterWidget, final DialogAction action) {
        this.presenterWidget = presenterWidget;
        this.action = action;
    }

    public static Builder builder(final PresenterWidget<?> presenterWidget) {
        return new Builder(presenterWidget);
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

    public PresenterWidget<?> getPresenterWidget() {
        return presenterWidget;
    }

    public DialogAction getAction() {
        return action;
    }

    public interface Handler extends EventHandler {

        void onDisable(DisablePopupEvent event);
    }

    public static class Builder {

        private final PresenterWidget<?> presenterWidget;
        private DialogAction action;

        public Builder(final PresenterWidget<?> presenterWidget) {
            this.presenterWidget = presenterWidget;
        }

        public Builder action(final DialogAction action) {
            this.action = action;
            return this;
        }

        public void fire() {
            presenterWidget.fireEvent(new DisablePopupEvent(presenterWidget, action));
        }
    }
}
