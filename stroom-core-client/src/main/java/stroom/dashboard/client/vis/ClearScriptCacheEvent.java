/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.client.vis;

import stroom.docref.DocRef;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ClearScriptCacheEvent extends GwtEvent<ClearScriptCacheEvent.Handler> {

    private static Type<Handler> TYPE;
    private final DocRef script;

    private ClearScriptCacheEvent(final DocRef script) {
        this.script = script;
    }

    public static Type<Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    public static void fire(final HasHandlers source) {
        source.fireEvent(new ClearScriptCacheEvent(null));
    }

    public static void fire(final HasHandlers source, final DocRef script) {
        source.fireEvent(new ClearScriptCacheEvent(script));
    }

    @Override
    public Type<Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final Handler handler) {
        handler.onClear(this);
    }

    public DocRef getScript() {
        return script;
    }

    public interface Handler extends EventHandler {

        void onClear(ClearScriptCacheEvent event);
    }
}
