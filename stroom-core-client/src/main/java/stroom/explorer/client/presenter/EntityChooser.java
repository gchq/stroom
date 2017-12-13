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

package stroom.explorer.client.presenter;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.explorer.shared.ExplorerNode;
import stroom.widget.util.client.SelectionType;

public class EntityChooser extends ExplorerDropDownTreePresenter {
    @Inject
    EntityChooser(final EventBus eventBus, final DropDownTreeView view,
                  final ClientDispatchAsync dispatcher) {
        super(eventBus, view, dispatcher);
        setIncludeNullSelection(false);
    }

    @Override
    protected void setSelectedTreeItem(final ExplorerNode selectedItem, final SelectionType selectionType, final boolean fireEvents) {
        super.setSelectedTreeItem(selectedItem, selectionType, false);
    }
}
