/*
 * Copyright 2016-2025 Crown Copyright
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

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.gwtplatform.mvp.client.PresenterWidget;

public class RenamePopupEvent extends GwtEvent<RenamePopupEvent.Handler> {

    private static Type<Handler> TYPE;
    private final PresenterWidget<?> presenterWidget;
    private final String caption;

    private RenamePopupEvent(final PresenterWidget<?> presenterWidget, final String caption) {
        this.presenterWidget = presenterWidget;
        this.caption = caption;
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
        handler.onRename(this);
    }

    public PresenterWidget<?> getPresenterWidget() {
        return presenterWidget;
    }

    public String getCaption() {
        return caption;
    }

    public interface Handler extends EventHandler {

        void onRename(RenamePopupEvent event);
    }

    public static class Builder {

        private final PresenterWidget<?> presenterWidget;
        private String caption;

        public Builder(final PresenterWidget<?> presenterWidget) {
            this.presenterWidget = presenterWidget;
        }

        public Builder caption(final String caption) {
            this.caption = caption;
            return this;
        }

        public void fire() {
            presenterWidget.fireEvent(new RenamePopupEvent(presenterWidget, caption));
        }
    }
}
