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

package stroom.widget.menu.client.presenter;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class HideMenuEvent
        extends GwtEvent<HideMenuEvent.Handler> {

    private static Type<Handler> TYPE;

    private HideMenuEvent() {
    }

    public static Builder builder() {
        return new Builder();
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
        handler.onHide(this);
    }

    public interface Handler extends EventHandler {

        void onHide(HideMenuEvent event);
    }

    public static class Builder {

        public Builder() {
        }

        public void fire(final HasHandlers hasHandlers) {
            hasHandlers.fireEvent(new HideMenuEvent());
        }
    }
}

