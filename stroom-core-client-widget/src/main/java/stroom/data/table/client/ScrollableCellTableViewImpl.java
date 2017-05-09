/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.data.table.client;

import com.google.gwt.user.cellview.client.CellTable;
import com.google.gwt.user.cellview.client.CellTable.Resources;
import com.google.gwt.user.client.ui.ScrollPanel;

public class ScrollableCellTableViewImpl<R> extends CellTableViewImpl<R> implements ScrollableCellTableView<R> {
    public ScrollableCellTableViewImpl(final boolean supportsSelection, final Resources resources) {
        super(supportsSelection, resources);

        final CellTable<R> cellTable = getCellTable();
        cellTable.getElement().getStyle().setProperty("minWidth", "200px");

        final ScrollPanel scrollPanel = new ScrollPanel(cellTable);
        scrollPanel.getElement().getStyle().setProperty("minWidth", "200px");
        scrollPanel.getElement().getStyle().setProperty("maxHeight", "300px");
        setWidget(scrollPanel);
    }
}
