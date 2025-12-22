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

import stroom.widget.htree.client.ShadowBox.Colors;
import stroom.widget.htree.client.treelayout.Bounds;
import stroom.widget.htree.client.treelayout.Dimension;
import stroom.widget.htree.client.treelayout.NodeExtentProvider;

import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.canvas.dom.client.Context2d.TextAlign;
import com.google.gwt.canvas.dom.client.CssColor;
import com.google.gwt.canvas.dom.client.TextMetrics;
import com.google.gwt.user.client.ui.HasText;

public final class TextCellRenderer<T extends HasText> implements CellRenderer<T>, NodeExtentProvider<T> {

    private static final int DEFAULT_TEXT_SIZE = 10;
    private static final int DEFAULT_TEXT_PADDING = 5;
    private static final Colors NORMAL = Colors.createDefault();
    private static final Colors OVER = Colors.createDefaultMouseOver();
    private static final Colors SELECTED = Colors.createDefaultSelection();
    private final int textSize = DEFAULT_TEXT_SIZE;
    private final int textPadding = DEFAULT_TEXT_PADDING;
    private final String font = textSize + "px Arial";
    private final String textColor = "black";
    private final Context2d shadowContext;
    private final Context2d backgroundContext;
    private final Context2d textContext;
    private final ShadowBox shadowBox;

    public TextCellRenderer(final Context2d shadowContext, final Context2d backgroundContext,
                            final Context2d textContext) {
        this.shadowContext = shadowContext;
        this.backgroundContext = backgroundContext;
        this.textContext = textContext;
        shadowBox = new ShadowBox();
    }

    @Override
    public void render(final Bounds bounds, final T item, final boolean mouseOver, final boolean selected) {
        if (selected) {
            shadowBox.draw(shadowContext, backgroundContext, bounds, SELECTED);
        } else if (mouseOver) {
            shadowBox.draw(shadowContext, backgroundContext, bounds, OVER);
        } else {
            shadowBox.draw(shadowContext, backgroundContext, bounds, NORMAL);
        }
        drawText(textContext, bounds, item.getText());
    }

    private void drawText(final Context2d ctx, final Bounds bounds, final String text) {
        final CssColor fill = CssColor.make(textColor);

        ctx.setFont(font);
        ctx.setTextAlign(TextAlign.LEFT);
        ctx.setFillStyle(fill);
        ctx.fillText(text, bounds.getX() + textPadding, bounds.getY() + textPadding + textSize - 1);
    }

    @Override
    public Dimension getExtents(final T treeNode) {
        textContext.setFont(font);
        final TextMetrics textMetrics = textContext.measureText(treeNode.getText());
        final double width = textMetrics.getWidth() + (textPadding * 2);
        final double height = textSize + (textPadding * 2);
        return new Dimension(width, height);
    }
}
