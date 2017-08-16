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

public class PopupSize {
    private static final int DEFAULT_MIN_WIDTH = 235;
    private static final int DEFAULT_MIN_HEIGHT = 160;

    private final int width;
    private final int height;
    private final Integer minWidth;
    private final Integer minHeight;
    private final Integer maxWidth;
    private final Integer maxHeight;
    private final boolean resizable;

    public PopupSize(final int width, final int height) {
        this(width, height, DEFAULT_MIN_WIDTH, DEFAULT_MIN_HEIGHT, false);
    }

    public PopupSize(final int width, final int height, final boolean resizable) {
        this(width, height, DEFAULT_MIN_WIDTH, DEFAULT_MIN_HEIGHT, resizable);
    }

    public PopupSize(final int width, final int height, final Integer minWidth, final Integer minHeight,
                     final boolean resizable) {
        this(width, height, minWidth, minHeight, null, null, resizable);
    }

    public PopupSize(final int width, final int height, final Integer minWidth, final Integer minHeight,
                     final Integer maxWidth, final Integer maxHeight, final boolean resizable) {
        this.width = width;
        this.height = height;
        this.minWidth = minWidth;
        this.minHeight = minHeight;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
        this.resizable = resizable;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public Integer getMinWidth() {
        return minWidth;
    }

    public Integer getMinHeight() {
        return minHeight;
    }

    public Integer getMaxWidth() {
        return maxWidth;
    }

    public Integer getMaxHeight() {
        return maxHeight;
    }

    public boolean isResizable() {
        return resizable;
    }
}
