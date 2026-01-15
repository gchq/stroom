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

package stroom.document.client.event;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class CloseSelectedDocumentEvent extends GwtEvent<CloseSelectedDocumentEvent.Handler> {

    private static Type<Handler> TYPE;
    private final Runnable runOnClose;
    private final boolean resizeTabBar;

    private CloseSelectedDocumentEvent(final Runnable runOnClose, final boolean resizeTabBar) {
        this.runOnClose = runOnClose;
        this.resizeTabBar = resizeTabBar;
    }

    public static void fire(final HasHandlers handlers, final Runnable runOnClose, final boolean resizeTabBar) {
        handlers.fireEvent(new CloseSelectedDocumentEvent(runOnClose, resizeTabBar));
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
        handler.onClose(this);
    }

    public Runnable runOnClose() {
        return runOnClose;
    }

    public boolean resizeTabBar() {
        return resizeTabBar;
    }

    public interface Handler extends EventHandler {
        void onClose(final CloseSelectedDocumentEvent event);
    }
}
