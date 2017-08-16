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

package stroom.widget.popup.client.presenter;

public class PopupPosition {
    private final int left;
    private final int right;
    private final int top;
    private final int bottom;
    private final HorizontalLocation horizontalLocation;
    private final VerticalLocation verticalLocation;
    public PopupPosition(final int x, final int y) {
        this(x, x, y, y, null, null);
    }
    public PopupPosition(final int left, final int right, final int top, final int bottom) {
        this(left, right, top, bottom, null, null);
    }

    public PopupPosition(final int left, final int right, final int top, final int bottom,
                         final HorizontalLocation horizontalLocation, final VerticalLocation verticalLocation) {
        this.left = left;
        this.right = right;
        this.top = top;
        this.bottom = bottom;
        this.horizontalLocation = horizontalLocation;
        this.verticalLocation = verticalLocation;
    }

    public int getLeft() {
        return left;
    }

    public int getRight() {
        return right;
    }

    public int getTop() {
        return top;
    }

    public int getBottom() {
        return bottom;
    }

    public HorizontalLocation getHorizontalLocation() {
        return horizontalLocation;
    }

    public VerticalLocation getVerticalLocation() {
        return verticalLocation;
    }

    public enum HorizontalLocation {
        LEFT, RIGHT
    }

    public enum VerticalLocation {
        ABOVE, BELOW
    }
}
