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

package stroom.data.grid.client;

import stroom.widget.menu.client.presenter.Item;

import com.google.gwt.cell.client.Cell.Context;

import java.util.List;

/**
 * Interface for cells to provide unique context menu items.
 * @param <C> The type of the cell  value.
 */
public interface HasContextMenus<C> {
    /**
     * Called by the data grid when a context menu is requested for the cell.
     * @param context The {@link Context} of the cell.
     * @param value The value of the cell.
     * @return A list of {@link Item}s to be added to the context menu, or null/empty if none.
     */
    List<Item> getContextMenuItems(Context context, C value);

}
