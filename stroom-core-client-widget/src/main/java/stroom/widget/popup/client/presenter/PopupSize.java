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

package stroom.widget.popup.client.presenter;

import com.google.gwt.user.client.Window;

public class PopupSize {

    private final Size width;
    private final Size height;

    private PopupSize(final Size width, final Size height) {
        this.width = width;
        this.height = height;
    }

    public Size getWidth() {
        return width;
    }

    public Size getHeight() {
        return height;
    }

    public static PopupSize resizable(final int initialWidth,
                                      final int initialHeight) {
        return PopupSize.builder()
                .width(Size.builder().initial(initialWidth).resizable(true).build())
                .height(Size.builder().initial(initialHeight).resizable(true).build())
                .build();
    }

    public static PopupSize resizable(final int initialWidth,
                                      final int initialHeight,
                                      final int minWidth,
                                      final int minHeight) {
        final int maxWidth = Window.getClientWidth();
        final int maxHeight = Window.getClientHeight();
        final int width = Math.max(Math.min(initialWidth, maxWidth), minWidth);
        final int height = Math.max(Math.min(initialHeight, maxHeight), minHeight);

        return PopupSize.builder()
                .width(Size.builder().initial(width).min(minWidth).resizable(true).build())
                .height(Size.builder().initial(height).min(minHeight).resizable(true).build())
                .build();
    }

    public static PopupSize resizable() {
        return PopupSize.builder()
                .width(Size.builder().resizable(true).build())
                .height(Size.builder().resizable(true).build())
                .build();
    }

    public static PopupSize resizableX() {
        return PopupSize.builder()
                .width(Size.builder().resizable(true).build())
                .build();
    }

    public static PopupSize resizableX(final int initialWidth) {
        return PopupSize.builder()
                .width(Size.builder().initial(initialWidth).resizable(true).build())
                .build();
    }

    public static PopupSize resizableY() {
        return PopupSize.builder()
                .height(Size.builder().resizable(true).build())
                .build();
    }

    public static PopupSize resizableY(final int initialHeight) {
        return PopupSize.builder()
                .height(Size.builder().initial(initialHeight).resizable(true).build())
                .build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private Size width;
        private Size height;

        public Builder width(final Size width) {
            this.width = width;
            return this;
        }

        public Builder height(final Size height) {
            this.height = height;
            return this;
        }

        public PopupSize build() {
            return new PopupSize(width, height);
        }
    }
}
