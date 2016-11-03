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

package stroom.dashboard.client.flexlayout;

public class PositionAndSize {
    private final int[] pos;
    private final int[] size;

    public PositionAndSize() {
        pos = new int[2];
        size = new int[2];
    }

    public int getLeft() {
        return pos[0];
    }

    public int getTop() {
        return pos[1];
    }

    public int getWidth() {
        return size[0];
    }

    public int getHeight() {
        return size[1];
    }

    public int getPos(final int dimension) {
        return pos[dimension];
    }

    public int[] getPos() {
        return pos;
    }

    public void setPos(final int dimension, final int pos) {
        this.pos[dimension] = pos;
    }

    public int getSize(final int dimension) {
        return size[dimension];
    }

    public int[] getSize() {
        return size;
    }

    public void setSize(final int dimension, final int size) {
        this.size[dimension] = size;
    }
}
