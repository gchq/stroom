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

package stroom.dashboard.client.flexlayout;

public class MutableSize {

    private int width;
    private int height;

    public MutableSize() {
    }

    public MutableSize(final int width,
                       final int height) {
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(final int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(final int height) {
        this.height = height;
    }

    public void set(final int dimension, final int size) {
        if (dimension == 0) {
            width = size;
        } else {
            height = size;
        }
    }

    public int get(final int dimension) {
        if (dimension == 0) {
            return width;
        }
        return height;
    }

    public MutableSize copy() {
        return new MutableSize(width, height);
    }

    @Override
    public String toString() {
        return "[" + width + ", " + height + "]";
    }
}
