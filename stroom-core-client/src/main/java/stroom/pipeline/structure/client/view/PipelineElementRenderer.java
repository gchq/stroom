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

package stroom.pipeline.structure.client.view;

import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.canvas.dom.client.Context2d.TextAlign;
import com.google.gwt.canvas.dom.client.CssColor;
import com.google.gwt.canvas.dom.client.TextMetrics;
import com.google.gwt.dom.client.ImageElement;
import com.google.gwt.user.client.ui.Image;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.structure.client.view.ImageCache.LoadCallback;
import stroom.widget.htree.client.CellRenderer;
import stroom.widget.htree.client.ShadowBox;
import stroom.widget.htree.client.ShadowBox.Colors;
import stroom.widget.htree.client.treelayout.Bounds;
import stroom.widget.htree.client.treelayout.Dimension;
import stroom.widget.htree.client.treelayout.NodeExtentProvider;

public final class PipelineElementRenderer
        implements CellRenderer<PipelineElement>, NodeExtentProvider<PipelineElement> {
    private static final int IMAGE_MARGIN = 4;
    private static final int DEFAULT_TEXT_SIZE = 10;
    private static final int DEFAULT_TEXT_PADDING = 5;

    private final int textSize = DEFAULT_TEXT_SIZE;
    private final int textPadding = DEFAULT_TEXT_PADDING;
    private final String font = textSize + "px Arial";

    private final String textColor = "black";

    private final Context2d shadowContext;
    private final Context2d backgroundContext;
    private final Context2d textContext;
    private final ShadowBox shadowBox;

    private static final Colors NORMAL = Colors.createDefault();
    private static final Colors OVER = Colors.createDefaultMouseOver();
    private static final Colors SELECTED = Colors.createDefaultSelection();

    public PipelineElementRenderer(final Context2d shadowContext, final Context2d backgroundContext,
            final Context2d textContext) {
        this.shadowContext = shadowContext;
        this.backgroundContext = backgroundContext;
        this.textContext = textContext;
        shadowBox = new ShadowBox();
    }

    @Override
    public void render(final Bounds bounds, final PipelineElement element, final boolean mouseOver,
            final boolean selected) {
        if (selected) {
            shadowBox.draw(shadowContext, backgroundContext, bounds, SELECTED);
        } else if (mouseOver) {
            shadowBox.draw(shadowContext, backgroundContext, bounds, OVER);
        } else {
            shadowBox.draw(shadowContext, backgroundContext, bounds, NORMAL);
        }
        drawText(textContext, bounds, element);
    }

    private void drawText(final Context2d ctx, final Bounds bounds, final PipelineElement element) {
        final double x = bounds.getX() + textPadding;
        final double y = bounds.getY() + textPadding + textSize - 1;

        final String url = PipelineImageUtil.getImageURL(element);
        if (url != null) {
            // Get the image element from the cache. This is necessary as the
            // canvas need images to be loaded before it can render them.
            ImageCache.getImage(url, new LoadCallback() {
                @Override
                public void onLoad(final ImageElement imageElement) {
                    // Draw the image.
                    drawImage(ctx, x, y, imageElement, element);
                }
            });
        } else {
            // We don't have an image so just render text.
            drawText(ctx, x, y, element);
        }
    }

    private void drawImage(final Context2d ctx, final double x, final double y, final ImageElement imageElement,
            final PipelineElement element) {
        if (imageElement != null) {
            ctx.drawImage(imageElement, x, y - 12);
            drawText(ctx, x + imageElement.getWidth() + IMAGE_MARGIN, y, element);

        } else {
            // We don't have an image so just render text.
            drawText(ctx, x, y, element);
        }
    }

    private void drawText(final Context2d ctx, final double x, final double y, final PipelineElement element) {
        final CssColor fill = CssColor.make(textColor);

        ctx.setFont(font);
        ctx.setTextAlign(TextAlign.LEFT);
        ctx.setFillStyle(fill);
        ctx.fillText(element.getId(), x, y);
    }

    @Override
    public Dimension getExtents(final PipelineElement element) {
        double width = 0;

        // Get image.
        final Image image = PipelineImageUtil.getImage(element.getElementType());
        if (image != null) {
            width += image.getWidth() + IMAGE_MARGIN;
        }

        textContext.setFont(font);
        final TextMetrics textMetrics = textContext.measureText(element.getId());
        width += textMetrics.getWidth();
        width += (textPadding * 2);

        final double height = textSize + (textPadding * 2);

        return new Dimension(width, height);
    }
}
