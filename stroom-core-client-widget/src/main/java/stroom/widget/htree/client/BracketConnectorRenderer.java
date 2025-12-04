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

import stroom.widget.htree.client.treelayout.TreeLayout;

import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.canvas.dom.client.CssColor;

public class BracketConnectorRenderer<T> implements ConnectorRenderer<T> {

    private static final double RADIUS = 4;
    private static final double HALF_PI = Math.PI * 0.5;
    private static final double ONE_AND_HALF_PI = Math.PI * 1.5;

    private final CssColor lineColor;

    private final Context2d ctx;

    public BracketConnectorRenderer(final Context2d ctx) {
        this.ctx = ctx;
        lineColor = CssColor.make("grey");
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
