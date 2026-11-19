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

package stroom.widget.htree.client;

import stroom.widget.htree.client.treelayout.TreeLayout;

import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.canvas.dom.client.CssColor;

import java.util.function.Predicate;

public class BracketConnectorRenderer<T> implements ConnectorRenderer<T> {

    // Where no theme token gives the colour
    private static final String FALLBACK_COLOUR = "grey";
    private static final double RADIUS = 4;
    private static final double HALF_PI = Math.PI * 0.5;
    private static final double ONE_AND_HALF_PI = Math.PI * 1.5;
    // As faded items are (e.g. those detached to be moved)
    private static final double FADED_ALPHA = 0.75;

    private CssColor lineColor = CssColor.make(FALLBACK_COLOUR);

    private final Context2d ctx;
    private final Predicate<T> faded;
    // The custom property (a theme token) that gives the brackets' colour, or null for grey
    private final String colourProperty;

    public BracketConnectorRenderer(final Context2d ctx) {
        this(ctx, item -> false, null);
    }

    /// @param ctx            Where to draw.
    /// @param faded          Whether an item is faded, whose bracket (to its children) is too.
    /// @param colourProperty The custom property (a theme token, e.g. "--expression-tree__bracket")
    ///                       that gives the brackets' colour, read from the canvas so that it
    ///                       follows a change of theme; or null for grey.
    public BracketConnectorRenderer(final Context2d ctx,
                                    final Predicate<T> faded,
                                    final String colourProperty) {
        this.ctx = ctx;
        this.faded = faded;
        this.colourProperty = colourProperty;
    }

    @Override
    public void render(final TreeLayout<T> treeLayout,
                       final T parent,
                       final T child,
                       final double x1,
                       final double y1,
                       final double x2,
                       final double y2,
                       final boolean firstChild,
                       final boolean lastChild) {
        // In forced colours (e.g. Windows High Contrast), which don't change what a canvas draws, the
        // user's text colour (the canvas's own colour, which they set), not faded, as fading lowers
        // the contrast the user has chosen
        final boolean forced = CanvasColours.isForcedColours();
        lineColor = CssColor.make(forced
                ? CanvasColours.getTextColour(ctx.getCanvas())
                : getThemeColour());
        ctx.setGlobalAlpha(faded.test(parent) && !forced
                ? FADED_ALPHA
                : 1);
        render(treeLayout, x1, y1, x2, y2, firstChild, lastChild);
        ctx.setGlobalAlpha(1);
    }

    private String getThemeColour() {
        if (colourProperty == null) {
            return FALLBACK_COLOUR;
        }
        final String colour = CanvasColours.getProperty(ctx.getCanvas(), colourProperty);
        return colour.isEmpty()
                ? FALLBACK_COLOUR
                : colour;
    }

    @Override
    public void render(final TreeLayout<T> treeLayout, final double x1, final double y1, final double x2,
                       final double y2, final boolean firstChild, final boolean lastChild) {
        final double midX = x1 + ((x2 - x1) / 2);
        final double minX = midX - RADIUS;
        final double maxX = midX + RADIUS;

        if (firstChild && lastChild) {
            drawTop(ctx, midX, minX, maxX, y1, y1 - 10);
            drawBottom(ctx, midX, minX, maxX, y1, y1 + 10);
        } else if (firstChild) {
            drawTop(ctx, midX, minX, maxX, y1, y2);
        } else if (lastChild) {
            drawBottom(ctx, midX, minX, maxX, y1, y2);
        }
    }

    private void drawTop(final Context2d ctx, final double midX, final double minX, final double maxX, final double y1,
                         final double y2) {
        final double maxY = y1 - RADIUS;
        final double minY = y2 + RADIUS;

        // Draw top of bracket
        ctx.beginPath();
        ctx.moveTo(midX, maxY);
        ctx.arc(minX, maxY, RADIUS, 0, HALF_PI);
        ctx.moveTo(midX, maxY);
        ctx.lineTo(midX, minY);
        ctx.moveTo(midX, minY);
        ctx.arc(maxX, minY, RADIUS, Math.PI, ONE_AND_HALF_PI);
        ctx.setStrokeStyle(lineColor);
        ctx.stroke();
    }

    private void drawBottom(final Context2d ctx, final double midX, final double minX, final double maxX,
                            final double y1, final double y2) {
        final double minY = y1 + RADIUS;
        final double maxY = y2 - RADIUS;

        // Draw bottom of bracket
        ctx.beginPath();
        ctx.moveTo(minX, y1);
        ctx.arc(minX, minY, RADIUS, ONE_AND_HALF_PI, 0);
        ctx.moveTo(midX, minY);
        ctx.lineTo(midX, maxY);
        ctx.moveTo(maxX, y2);
        ctx.arc(maxX, maxY, RADIUS, HALF_PI, Math.PI);
        ctx.setStrokeStyle(lineColor);
        ctx.stroke();
    }
}
