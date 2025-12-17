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

package stroom.widget.tab.client.view;

import stroom.widget.tab.client.presenter.TabData;

public class DraggedTab {

    private final TabData tabData;
    private int index;
    private final int tabWidth;

    public DraggedTab(final TabData tabData, final int index, final int tabWidth) {
        this.tabData = tabData;
        this.index = index;
        this.tabWidth = tabWidth;
    }


    public TabData getTabData() {
        return tabData;
    }

    public int getIndex() {
        return index;
    }

    public int getTabWidth() {
        return tabWidth;
    }

    public void setIndex(final int index) {
        this.index = index;
    }
}
