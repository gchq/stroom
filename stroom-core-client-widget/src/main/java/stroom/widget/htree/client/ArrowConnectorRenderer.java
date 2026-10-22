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

public class ArrowConnectorRenderer<T> implements ConnectorRenderer<T> {

    private static final double HEAD_SIZE = 3;
    private static final double EXAGERATE_CURVE = 0;

    private final CssColor lineColor;
    private final CssColor headColor;

    private final Context2d ctx;
    // The width of the line, and the length of a solid arrow head (or 0 for the open head of
    // HEAD_SIZE)
    private final double lineWidth;
    private final double solidHeadLength;

    public ArrowConnectorRenderer(final Context2d ctx) {
        this(ctx, 1, 0);
    }

    /// Draws connectors as lines of a width, ending in a solid arrow head.
    ///
    /// @param ctx             The context to draw on.
    /// @param lineWidth       The width of the lines, in pixels.
    /// @param solidHeadLength The length of the arrow heads, in pixels, which are as wide as they
    ///                        are long; or 0 for an open arrow head of lines.
    public ArrowConnectorRenderer(final Context2d ctx, final double lineWidth, final double solidHeadLength) {
        this.ctx = ctx;
        this.lineWidth = lineWidth;
        this.solidHeadLength = solidHeadLength;
        lineColor = CssColor.make("grey");
        headColor = CssColor.make("grey");
    }

    @Override
    public void render(final TreeLayout<T> treeLayout, final double x1, final double y1, final double x2,
                       final double y2, final boolean firstChild, final boolean lastChild) {
        // A solid head takes the end of the line, so that a wide line doesn't show past its point
        final double lineEndX = x2 - solidHeadLength;
        final double midx = x1 + ((lineEndX - x1) / 2);

        // Draw arrow line.
        ctx.beginPath();
        ctx.moveTo(x1, y1);
        ctx.bezierCurveTo(midx + EXAGERATE_CURVE, y1, midx - EXAGERATE_CURVE, y2, lineEndX, y2);
        ctx.setLineWidth(lineWidth);
        ctx.setStrokeStyle(lineColor);
        ctx.stroke();

        // Draw arrow head.
        ctx.beginPath();
        if (solidHeadLength > 0) {
            final double halfWidth = solidHeadLength / 2;
            ctx.moveTo(x2, y2);
            ctx.lineTo(lineEndX, y2 - halfWidth);
            ctx.lineTo(lineEndX, y2 + halfWidth);
            ctx.closePath();
            ctx.setFillStyle(headColor);
            ctx.fill();
        } else {
            ctx.moveTo(x2, y2);
            ctx.lineTo(x2 - HEAD_SIZE, y2 - HEAD_SIZE);
            ctx.moveTo(x2, y2);
            ctx.lineTo(x2 - HEAD_SIZE, y2 + HEAD_SIZE);
            ctx.setStrokeStyle(headColor);
            ctx.stroke();
        }
    }
}
