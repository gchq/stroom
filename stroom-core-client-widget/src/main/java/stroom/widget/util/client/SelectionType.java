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

package stroom.widget.util.client;

public class SelectionType {

    private final boolean doubleSelect;
    private final boolean rightClick;
    private final boolean allowMultiSelect;
    private final boolean controlPressed;
    private final boolean shiftPressed;

    public SelectionType() {
        this(false, false, false, false, false);
    }

    public SelectionType(final boolean doubleSelect,
                         final boolean rightClick,
                         final boolean allowMultiSelect,
                         final boolean controlPressed,
                         final boolean shiftPressed) {
        this.doubleSelect = doubleSelect;
        this.rightClick = rightClick;
        this.allowMultiSelect = allowMultiSelect;
        this.controlPressed = controlPressed;
        this.shiftPressed = shiftPressed;
    }

    public boolean isDoubleSelect() {
        return doubleSelect;
    }

    public boolean isAllowMultiSelect() {
        return allowMultiSelect;
    }

    public boolean isControlPressed() {
        return controlPressed;
    }

    public boolean isShiftPressed() {
        return shiftPressed;
    }

    public boolean isMultiSelect() {
        return controlPressed || shiftPressed;
    }

    public boolean isRightClick() {
        return rightClick;
    }
}
