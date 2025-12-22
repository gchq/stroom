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

package stroom.widget.htree.client.treelayout;

public class Bounds {

    private final double x;
    private final double y;
    private final double width;
    private final double height;

    public Bounds(final double x, final double y, final double width, final double height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getWidth() {
        return width;
    }

    public double getHeight() {
        return height;
    }

    /**
     * Returns the smallest X coordinate of the framing rectangle of the
     * <code>Shape</code> in <code>double</code> precision.
     *
     * @return the smallest X coordinate of the framing rectangle of the
     * <code>Shape</code>.
     * @since 1.2
     */
    public double getMinX() {
        return getX();
    }

    /**
     * Returns the smallest Y coordinate of the framing rectangle of the
     * <code>Shape</code> in <code>double</code> precision.
     *
     * @return the smallest Y coordinate of the framing rectangle of the
     * <code>Shape</code>.
     * @since 1.2
     */
    public double getMinY() {
        return getY();
    }

    /**
     * Returns the largest X coordinate of the framing rectangle of the
     * <code>Shape</code> in <code>double</code> precision.
     *
     * @return the largest X coordinate of the framing rectangle of the
     * <code>Shape</code>.
     * @since 1.2
     */
    public double getMaxX() {
        return getX() + getWidth();
    }

    /**
     * Returns the largest Y coordinate of the framing rectangle of the
     * <code>Shape</code> in <code>double</code> precision.
     *
     * @return the largest Y coordinate of the framing rectangle of the
     * <code>Shape</code>.
     * @since 1.2
     */
    public double getMaxY() {
        return getY() + getHeight();
    }

    /**
     * Returns the X coordinate of the center of the framing rectangle of the
     * <code>Shape</code> in <code>double</code> precision.
     *
     * @return the X coordinate of the center of the framing rectangle of the
     * <code>Shape</code>.
     * @since 1.2
     */
    public double getCenterX() {
        return getX() + getWidth() / 2.0;
    }

    /**
     * Returns the Y coordinate of the center of the framing rectangle of the
     * <code>Shape</code> in <code>double</code> precision.
     *
     * @return the Y coordinate of the center of the framing rectangle of the
     * <code>Shape</code>.
     * @since 1.2
     */
    public double getCenterY() {
        return getY() + getHeight() / 2.0;
    }

    public Dimension getSize() {
        return new Dimension(width, height);
    }
}
