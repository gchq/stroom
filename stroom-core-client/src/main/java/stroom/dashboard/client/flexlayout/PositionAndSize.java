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

public class PositionAndSize {

    private final double[] pos;
    private final double[] size;

    public PositionAndSize() {
        pos = new double[2];
        size = new double[2];
    }

    public double getLeft() {
        return pos[0];
    }

    public double getTop() {
        return pos[1];
    }

    public double getWidth() {
        return size[0];
    }

    public double getHeight() {
        return size[1];
    }

    public double getPos(final int dimension) {
        return pos[dimension];
    }

    public double[] getPos() {
        return pos;
    }

    public void setPos(final int dimension, final double pos) {
        this.pos[dimension] = pos;
    }

    public double getSize(final int dimension) {
        return size[dimension];
    }

    public double[] getSize() {
        return size;
    }

    public void setSize(final int dimension, final double size) {
        this.size[dimension] = size;
    }

    public PositionAndSize copy() {
        final PositionAndSize positionAndSize = new PositionAndSize();
        positionAndSize.pos[0] = pos[0];
        positionAndSize.pos[1] = pos[1];
        positionAndSize.size[0] = size[0];
        positionAndSize.size[1] = size[1];
        return positionAndSize;
    }
}
