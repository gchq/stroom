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

package stroom.widget.htree.client;

import stroom.widget.htree.client.treelayout.Bounds;

import com.google.gwt.canvas.dom.client.CanvasGradient;
import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.canvas.dom.client.CssColor;
import com.google.gwt.canvas.dom.client.FillStrokeStyle;

public class ShadowBox {

    private static final double DEFAULT_RADIUS = 5;
    private final RoundedRectangle roundedRectangle = new RoundedRectangle();
    private final double radius = DEFAULT_RADIUS;
    private final double shadowOffset = 1;

    public void draw(final Context2d shadowContext, final Context2d backgroundContext, final Bounds bounds,
                     final Colors colors) {
        final Bounds shadowBounds = getShadowBounds(bounds);
        clear(shadowContext, shadowBounds);
        clear(backgroundContext, shadowBounds);

        drawShadow(shadowContext, shadowBounds, colors);
        drawBackground(backgroundContext, bounds, colors);
    }

    public void clear(final Context2d shadowContext, final Context2d backgroundContext, final Bounds bounds) {
        final Bounds shadowBounds = getShadowBounds(bounds);
        clear(shadowContext, shadowBounds);
        clear(backgroundContext, shadowBounds);
    }

    private Bounds getShadowBounds(final Bounds bounds) {
        return new Bounds(bounds.getX() + shadowOffset, bounds.getY() + shadowOffset, bounds.getWidth(),
                bounds.getHeight());
    }

    private void drawShadow(final Context2d ctx, final Bounds bounds, final Colors colors) {
        final CssColor stroke = CssColor.make(colors.shadowColor);
        roundedRectangle.draw(ctx, bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), radius, stroke,
                stroke);
    }

    private void drawBackground(final Context2d ctx, final Bounds bounds, final Colors colors) {
        FillStrokeStyle fill = null;
        if (colors.backgroundTopColor.equals(colors.backgroundBottomColor)) {
            fill = CssColor.make(colors.backgroundTopColor);
        } else {
            final CanvasGradient gradient = ctx.createLinearGradient(bounds.getX(), bounds.getY(), bounds.getX(),
                    bounds.getMaxY());
            gradient.addColorStop(0, colors.backgroundTopColor);
            gradient.addColorStop(1, colors.backgroundBottomColor);
            fill = gradient;
        }

        CssColor stroke = null;
        stroke = CssColor.make(colors.borderColor);

        roundedRectangle.draw(ctx, bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), radius, fill,
                stroke);
    }

    private void clear(final Context2d ctx, final Bounds bounds) {
        ctx.clearRect(bounds.getX() - 2, bounds.getY() - 2, bounds.getWidth() + 4, bounds.getHeight() + 4);
    }

    public static class Colors {

        private static final String DEFAULT_BORDER_COLOR = "#c5cde2";
        private static final String DEFAULT_BACKGROUND_TOP_COLOR = "#f5f9fd";
        private static final String DEFAULT_BACKGROUND_BOTTOM_COLOR = "#e1ebf8";
        private static final String DEFAULT_SHADOW_COLOR = "#888888";

        private final String borderColor;
        private final String backgroundTopColor;
        private final String backgroundBottomColor;
        private final String shadowColor;

        public Colors(final String borderColor, final String backgroundTopColor, final String backgroundBottomColor,
                      final String shadowColor) {
            this.borderColor = borderColor;
            this.backgroundTopColor = backgroundTopColor;
            this.backgroundBottomColor = backgroundBottomColor;
            this.shadowColor = shadowColor;
        }

        public static Colors createDefault() {
            return new Colors(DEFAULT_BORDER_COLOR, DEFAULT_BACKGROUND_TOP_COLOR, DEFAULT_BACKGROUND_BOTTOM_COLOR,
                    DEFAULT_SHADOW_COLOR);
        }

        public static Colors createDefaultSelection() {
            return new Colors("black", "#bbdefb", "#bbdefb", DEFAULT_SHADOW_COLOR);
        }

        public static Colors createDefaultMouseOver() {
            return new Colors(DEFAULT_BORDER_COLOR, "#f7fbff", "#f7fbff", DEFAULT_SHADOW_COLOR);
        }
    }
}
