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

package stroom.cell.list.client;

import com.google.gwt.cell.client.Cell;
import com.google.gwt.core.shared.GWT;
import com.google.gwt.user.cellview.client.CellList;

public class CustomCellList<T> extends CellList<T> {

    private static Resources resources;

    public CustomCellList(final Cell<T> cell) {
        super(cell, getResources());
    }

    private static Resources getResources() {
        if (resources == null) {
            resources = GWT.create(Resources.class);
            resources.cellListStyle().ensureInjected();
        }
        return resources;
    }

    /**
     * A ClientBundle that provides images for this widget.
     */
    public interface Resources extends CellList.Resources {

        /**
         * The styles used in this widget.
         */
        @Override
        @Source("CellList.css")
        Style cellListStyle();
    }
}
