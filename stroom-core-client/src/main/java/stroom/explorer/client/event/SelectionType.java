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

package stroom.explorer.client.event;

public class SelectionType {
    private final boolean doubleSelect;
    private final boolean rightClick;
    private final boolean multiSelect;

    public SelectionType(final boolean doubleSelect, final boolean rightClick, final boolean multiSelect) {
        this.doubleSelect = doubleSelect;
        this.rightClick = rightClick;
        this.multiSelect = multiSelect;
    }

    public boolean isDoubleSelect() {
        return doubleSelect;
    }

    public boolean isMultiSelect() {
        return multiSelect;
    }

    public boolean isRightClick() {
        return rightClick;
    }
}
