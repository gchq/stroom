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

package stroom.explorer.client.event;

import stroom.explorer.shared.ExplorerNode;
import stroom.widget.util.client.MultiSelectionModel;
import stroom.widget.util.client.SelectionType;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;

public class ExplorerTreeSelectEvent extends GwtEvent<ExplorerTreeSelectEvent.Handler> {

    private static Type<ExplorerTreeSelectEvent.Handler> TYPE;
    private final MultiSelectionModel<ExplorerNode> selectionModel;
    private final SelectionType selectionType;

    public ExplorerTreeSelectEvent(final MultiSelectionModel<ExplorerNode> selectionModel,
                                   final SelectionType selectionType) {
        this.selectionModel = selectionModel;
        this.selectionType = selectionType;
    }

    public static void fire(final HasHandlers source,
                            final MultiSelectionModel<ExplorerNode> selectionModel,
                            final SelectionType selectionType) {
        source.fireEvent(new ExplorerTreeSelectEvent(selectionModel, selectionType));
    }

    public static Type<ExplorerTreeSelectEvent.Handler> getType() {
        if (TYPE == null) {
            TYPE = new Type<>();
        }
        return TYPE;
    }

    @Override
    public Type<ExplorerTreeSelectEvent.Handler> getAssociatedType() {
        return getType();
    }

    @Override
    protected void dispatch(final ExplorerTreeSelectEvent.Handler handler) {
        handler.onSelect(this);
    }

    public MultiSelectionModel<ExplorerNode> getSelectionModel() {
        return selectionModel;
    }

    public SelectionType getSelectionType() {
        return selectionType;
    }

    public interface Handler extends EventHandler {

        void onSelect(ExplorerTreeSelectEvent event);
    }
}
