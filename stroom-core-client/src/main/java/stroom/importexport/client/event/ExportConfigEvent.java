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

package stroom.importexport.client.event;

import stroom.explorer.shared.ExplorerNode;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

import java.util.List;

public class ExportConfigEvent extends GwtEvent<ExportConfigEvent.Handler> {

    private static Type<Handler> TYPE;

    private final List<ExplorerNode> selection;

    private ExportConfigEvent(final List<ExplorerNode> selection) {
        this.selection = selection;
    }

    public static void fire(final HasHandlers source, final List<ExplorerNode> selection) {
        source.fireEvent(new ExportConfigEvent(selection));
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
        handler.onExport(this);
    }

    public List<ExplorerNode> getSelection() {
        return selection;
    }

    public interface Handler extends EventHandler {

        void onExport(ExportConfigEvent event);
    }
}
