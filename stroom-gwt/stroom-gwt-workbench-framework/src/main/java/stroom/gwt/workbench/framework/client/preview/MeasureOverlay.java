/*
 * Copyright 2026 Crown Copyright
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

package stroom.gwt.workbench.framework.client.preview;

import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.CanvasElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// The measure tool: when enabled, hovering over an element in the preview draws its box model
/// (margin, border, padding and content) with labelled sizes on a canvas over the page.
///
/// This is a port of React Storybook's measure addon, so it looks and behaves the same. Derived
/// from Storybook, Copyright (c) 2024 Storybook, MIT licence (see NOTICE.md).
public class MeasureOverlay {

    private static final String CANVAS_ID = "workbench-measure";

    // Colours of the boxes and labels, as in Storybook
    private static final String COLOUR_MARGIN = "#f6b26b";
    private static final String COLOUR_BORDER = "#ffe599";
    private static final String COLOUR_PADDING = "#93c47d";
    private static final String COLOUR_CONTENT = "#6fa8dc";
    private static final String TEXT = "#232020";
    private static final String MARGIN_FILL = "#f6b26ba8";
    private static final String BORDER_FILL = "#ffe599a8";
    private static final String PADDING_FILL = "#93c47d8c";
    private static final String CONTENT_FILL = "#6fa8dca8";

    private static final int LABEL_PADDING = 6;
    private static final int SMALL_NODE_SIZE = 30;

    private static final String TOP = "top";
    private static final String RIGHT = "right";
    private static final String BOTTOM = "bottom";
    private static final String LEFT = "left";
    private static final String CENTER = "center";

    private CanvasElement canvas;
    private Context2d context;
    private boolean enabled;
    private JavaScriptObject listeners;
    private double pointerX;
    private double pointerY;

    /// Turns the tool on or off.
    ///
    /// @param enabled True to turn it on.
    public void setEnabled(final boolean enabled) {
        if (enabled == this.enabled) {
            return;
        }
        this.enabled = enabled;
        if (enabled) {
            createCanvas();
            listeners = addListeners();
        } else {
            removeListeners(listeners);
            listeners = null;
            destroyCanvas();
        }
    }

    // ---------- Called from the JS listeners ----------

    private void onPointerMove(final double x, final double y) {
        pointerX = x;
        pointerY = y;
    }

    private void onPointerOver(final double x, final double y) {
        pointerX = x;
        pointerY = y;
        drawAt(x, y);
    }

    private void onResize() {
        if (canvas != null) {
            sizeCanvas();
            drawAt(pointerX, pointerY);
        }
    }

    private native JavaScriptObject addListeners() /*-{
        var self = this;
        var move = $entry(function (e) {
            self.@stroom.gwt.workbench.framework.client.preview.MeasureOverlay::onPointerMove(DD)(e.clientX, e.clientY);
        });
        var over = $entry(function (e) {
            $wnd.requestAnimationFrame(function () {
                self.@stroom.gwt.workbench.framework.client.preview.MeasureOverlay::onPointerOver(DD)(
                    e.clientX, e.clientY);
            });
        });
        var resize = $entry(function () {
            $wnd.requestAnimationFrame(function () {
                self.@stroom.gwt.workbench.framework.client.preview.MeasureOverlay::onResize()();
            });
        });
        $doc.addEventListener('pointermove', move);
        $doc.addEventListener('pointerover', over);
        $wnd.addEventListener('resize', resize);
        return {move: move, over: over, resize: resize};
    }-*/;

    private static native void removeListeners(JavaScriptObject listeners) /*-{
        if (listeners) {
            $doc.removeEventListener('pointermove', listeners.move);
            $doc.removeEventListener('pointerover', listeners.over);
            $wnd.removeEventListener('resize', listeners.resize);
        }
    }-*/;

    // ---------- Canvas ----------

    private void createCanvas() {
        canvas = Document.get().createCanvasElement();
        canvas.setId(CANVAS_ID);
        canvas.getStyle().setProperty("position", "absolute");
        canvas.getStyle().setProperty("left", "0");
        canvas.getStyle().setProperty("top", "0");
        canvas.getStyle().setProperty("zIndex", "2147483647");
        canvas.getStyle().setProperty("pointerEvents", "none");
        context = canvas.getContext2d();
        Document.get().getBody().appendChild(canvas);
        sizeCanvas();
    }

    private void sizeCanvas() {
        // Shrink first so the canvas doesn't affect the size of the document
        setCanvasSize(0, 0);
        setCanvasSize(getDocumentWidth(), getDocumentHeight());
    }

    private void setCanvasSize(final double width, final double height) {
        canvas.getStyle().setProperty("width", width + "px");
        canvas.getStyle().setProperty("height", height + "px");
        final double scale = getDevicePixelRatio();
        canvas.setWidth((int) Math.floor(width * scale));
        canvas.setHeight((int) Math.floor(height * scale));
        context.setTransform(1, 0, 0, 1, 0, 0);
        context.scale(scale, scale);
    }

    private void destroyCanvas() {
        if (canvas != null) {
            canvas.removeFromParent();
            canvas = null;
            context = null;
        }
    }

    private void clear() {
        context.clearRect(0, 0, getDocumentWidth(), getDocumentHeight());
    }

    // ---------- Drawing ----------

    private void drawAt(final double x, final double y) {
        if (!enabled || context == null) {
            return;
        }
        clear();
        final Element element = elementFromPoint(x, y);
        if (element == null || element == canvas) {
            return;
        }
        final Measurements m = measure(element);
        final List<Label> labels = new ArrayList<>();
        labels.addAll(drawContent(m));
        labels.addAll(drawPadding(m));
        labels.addAll(drawBorder(m));
        labels.addAll(drawMargin(m));
        final boolean external = m.width <= SMALL_NODE_SIZE * 3 || m.height <= SMALL_NODE_SIZE;
        drawLabelStacks(m, labels, external);
    }

    private List<Label> drawMargin(final Measurements m) {
        final double marginHeight = m.height + m.margin.bottom + m.margin.top;
        context.setFillStyle(MARGIN_FILL);
        context.fillRect(m.left, m.top - m.margin.top, m.width, m.margin.top);
        context.fillRect(m.right, m.top - m.margin.top, m.margin.right, marginHeight);
        context.fillRect(m.left, m.bottom, m.width, m.margin.bottom);
        context.fillRect(m.left - m.margin.left, m.top - m.margin.top, m.margin.left, marginHeight);
        return nonZero(Type.MARGIN, m.margin);
    }

    private List<Label> drawPadding(final Measurements m) {
        final double paddingWidth = m.width - m.border.left - m.border.right;
        final double paddingHeight = m.height - m.padding.top - m.padding.bottom
                                     - m.border.top - m.border.bottom;
        context.setFillStyle(PADDING_FILL);
        context.fillRect(m.left + m.border.left, m.top + m.border.top, paddingWidth, m.padding.top);
        context.fillRect(m.right - m.padding.right - m.border.right,
                m.top + m.padding.top + m.border.top, m.padding.right, paddingHeight);
        context.fillRect(m.left + m.border.left, m.bottom - m.padding.bottom - m.border.bottom,
                paddingWidth, m.padding.bottom);
        context.fillRect(m.left + m.border.left, m.top + m.padding.top + m.border.top,
                m.padding.left, paddingHeight);
        return nonZero(Type.PADDING, m.padding);
    }

    private List<Label> drawBorder(final Measurements m) {
        final double borderHeight = m.height - m.border.top - m.border.bottom;
        context.setFillStyle(BORDER_FILL);
        context.fillRect(m.left, m.top, m.width, m.border.top);
        context.fillRect(m.left, m.bottom - m.border.bottom, m.width, m.border.bottom);
        context.fillRect(m.left, m.top + m.border.top, m.border.left, borderHeight);
        context.fillRect(m.right - m.border.right, m.top + m.border.top, m.border.right, borderHeight);
        return nonZero(Type.BORDER, m.border);
    }

    private List<Label> drawContent(final Measurements m) {
        final double contentWidth = m.contentWidth();
        final double contentHeight = m.contentHeight();
        context.setFillStyle(CONTENT_FILL);
        context.fillRect(m.left + m.border.left + m.padding.left, m.top + m.border.top + m.padding.top,
                contentWidth, contentHeight);
        final List<Label> labels = new ArrayList<>();
        labels.add(new Label(Type.CONTENT, CENTER, round(contentWidth) + " x " + round(contentHeight)));
        return labels;
    }

    private static List<Label> nonZero(final Type type, final Edges edges) {
        final List<Label> labels = new ArrayList<>();
        addIfNonZero(labels, type, TOP, edges.top);
        addIfNonZero(labels, type, RIGHT, edges.right);
        addIfNonZero(labels, type, BOTTOM, edges.bottom);
        addIfNonZero(labels, type, LEFT, edges.left);
        return labels;
    }

    private static void addIfNonZero(final List<Label> labels,
                                     final Type type,
                                     final String position,
                                     final double value) {
        if (value != 0) {
            labels.add(new Label(type, position, round(value)));
        }
    }

    private void drawLabelStacks(final Measurements m, final List<Label> labels, final boolean external) {
        final Map<String, List<Label>> stacks = new LinkedHashMap<>();
        for (final String position : List.of(TOP, RIGHT, BOTTOM, LEFT, CENTER)) {
            stacks.put(position, new ArrayList<>());
        }
        labels.forEach(label -> stacks.get(label.position).add(label));
        for (final List<Label> stack : stacks.values()) {
            Rect previous = null;
            for (final Label label : stack) {
                previous = external && CENTER.equals(label.position)
                        ? drawFloatingLabel(m, label)
                        : drawLabel(m, label, previous, external);
            }
        }
    }

    private Rect drawLabel(final Measurements m,
                           final Label label,
                           final Rect previous,
                           final boolean external) {
        double x = m.positionX(label.position);
        double y = m.positionY(label.position);
        final double[] offset = labelOffset(label.type, label.position, m, external);
        x += offset[0];
        y += offset[1];
        final Rect size = configureText(label.text);
        Rect rect = new Rect(x, y, size.w, size.h);
        if (previous != null && collide(rect, previous)) {
            rect = overlapAdjustment(label.position, rect, previous);
        }
        return textWithRect(label.type, rect, label.text);
    }

    private Rect drawFloatingLabel(final Measurements m, final Label label) {
        final Rect size = configureText(label.text);
        final double deltaW = size.w * 0.5 + LABEL_PADDING;
        final double deltaH = size.h * 0.5 + LABEL_PADDING;
        final double x = (m.alignLeft
                ? m.extremityLeft - deltaW
                : m.extremityRight + deltaW);
        final double y = (m.alignTop
                ? m.extremityTop - deltaH
                : m.extremityBottom + deltaH);
        return textWithRect(label.type, new Rect(x, y, size.w, size.h), label.text);
    }

    private static double[] labelOffset(final Type type,
                                        final String position,
                                        final Measurements m,
                                        final boolean external) {
        final double multiplier = external
                ? 1
                : 0.5;
        final double paddingShift = external
                ? (LABEL_PADDING + 1) * 2
                : 0;
        final double shift;
        final double padding = m.padding.get(position);
        final double border = m.border.get(position);
        final double margin = m.margin.get(position);
        switch (type) {
            case PADDING:
                shift = padding * multiplier + paddingShift;
                break;
            case BORDER:
                shift = padding + border * multiplier + paddingShift;
                break;
            case MARGIN:
                shift = padding + border + margin * multiplier + paddingShift;
                break;
            default:
                shift = 0;
                break;
        }
        switch (position) {
            case TOP:
                return new double[]{0, -shift};
            case RIGHT:
                return new double[]{shift, 0};
            case BOTTOM:
                return new double[]{0, shift};
            case LEFT:
                return new double[]{-shift, 0};
            default:
                return new double[]{0, 0};
        }
    }

    private static boolean collide(final Rect a, final Rect b) {
        return Math.abs(a.x - b.x) < Math.abs(a.w + b.w) / 2
               && Math.abs(a.y - b.y) < Math.abs(a.h + b.h) / 2;
    }

    private static Rect overlapAdjustment(final String position, final Rect current, final Rect previous) {
        double x = current.x;
        double y = current.y;
        switch (position) {
            case TOP:
                y = previous.y - previous.h - LABEL_PADDING;
                break;
            case RIGHT:
                x = previous.x + previous.w / 2 + LABEL_PADDING + current.w / 2;
                break;
            case BOTTOM:
                y = previous.y + previous.h + LABEL_PADDING;
                break;
            case LEFT:
                x = previous.x - previous.w / 2 - LABEL_PADDING - current.w / 2;
                break;
            default:
                break;
        }
        return new Rect(x, y, current.w, current.h);
    }

    private Rect configureText(final String text) {
        context.setFont("600 12px monospace");
        context.setTextBaseline(Context2d.TextBaseline.MIDDLE);
        context.setTextAlign(Context2d.TextAlign.CENTER);
        final double[] metrics = measureText(context, text);
        return new Rect(0, 0, metrics[0] + LABEL_PADDING * 2, metrics[1] + LABEL_PADDING * 2);
    }

    private Rect textWithRect(final Type type, final Rect rect, final String text) {
        roundedRect(rect, 3);
        context.setFillStyle(type.colour + "dd");
        context.fill();
        context.setStrokeStyle(type.colour);
        context.stroke();
        context.setFillStyle(TEXT);
        context.fillText(text, rect.x, rect.y);
        return rect;
    }

    private void roundedRect(final Rect rect, final double radius) {
        final double x = rect.x - rect.w / 2;
        final double y = rect.y - rect.h / 2;
        double r = radius;
        if (rect.w < 2 * r) {
            r = rect.w / 2;
        }
        if (rect.h < 2 * r) {
            r = rect.h / 2;
        }
        context.beginPath();
        context.moveTo(x + r, y);
        context.arcTo(x + rect.w, y, x + rect.w, y + rect.h, r);
        context.arcTo(x + rect.w, y + rect.h, x, y + rect.h, r);
        context.arcTo(x, y + rect.h, x, y, r);
        context.arcTo(x, y, x + rect.w, y, r);
        context.closePath();
    }

    private static String round(final double value) {
        if (value == Math.rint(value)) {
            return String.valueOf((long) value);
        }
        return formatTwoDecimals(value);
    }

    private static native String formatTwoDecimals(double value) /*-{
        return value.toFixed(2);
    }-*/;

    // ---------- Measuring ----------

    private static Measurements measure(final Element element) {
        final double[] rect = getBoundingClientRect(element);
        final double scrollX = getScrollX();
        final double scrollY = getScrollY();
        final Measurements m = new Measurements();
        m.margin = new Edges(element, "margin", "");
        m.padding = new Edges(element, "padding", "");
        m.border = new Edges(element, "border", "Width");
        m.top = rect[0] + scrollY;
        m.left = rect[1] + scrollX;
        m.bottom = rect[2] + scrollY;
        m.right = rect[3] + scrollX;
        m.width = rect[4];
        m.height = rect[5];
        m.extremityTop = m.top - m.margin.top;
        m.extremityBottom = m.bottom + m.margin.bottom;
        m.extremityLeft = m.left - m.margin.left;
        m.extremityRight = m.right + m.margin.right;
        // Put floating labels on the side with the most room
        final double distanceTop = Math.abs(scrollY - m.extremityTop);
        final double distanceBottom = Math.abs(scrollY + getInnerHeight() - m.extremityBottom);
        final double distanceLeft = Math.abs(scrollX - m.extremityLeft);
        final double distanceRight = Math.abs(scrollX + getInnerWidth() - m.extremityRight);
        m.alignLeft = distanceLeft > distanceRight;
        m.alignTop = distanceTop > distanceBottom;
        return m;
    }

    private static native Element elementFromPoint(double x, double y) /*-{
        return $doc.elementFromPoint(x, y);
    }-*/;

    private static native double[] getBoundingClientRect(Element element) /*-{
        var r = element.getBoundingClientRect();
        return [r.top, r.left, r.bottom, r.right, r.width, r.height];
    }-*/;

    private static native double getComputedPx(Element element, String property) /*-{
        var value = $wnd.getComputedStyle(element)[property];
        return parseInt(value, 10) || 0;
    }-*/;

    private static native double[] measureText(Context2d context, String text) /*-{
        var metrics = context.measureText(text);
        return [metrics.width, metrics.actualBoundingBoxAscent + metrics.actualBoundingBoxDescent];
    }-*/;

    private static native double getDocumentWidth() /*-{
        var e = $doc.documentElement;
        return Math.max(e.scrollWidth, e.offsetWidth);
    }-*/;

    private static native double getDocumentHeight() /*-{
        var e = $doc.documentElement;
        return Math.max(e.scrollHeight, e.offsetHeight);
    }-*/;

    private static native double getDevicePixelRatio() /*-{
        return $wnd.devicePixelRatio || 1;
    }-*/;

    private static native double getScrollX() /*-{
        return $wnd.scrollX;
    }-*/;

    private static native double getScrollY() /*-{
        return $wnd.scrollY;
    }-*/;

    private static native double getInnerWidth() /*-{
        return $wnd.innerWidth;
    }-*/;

    private static native double getInnerHeight() /*-{
        return $wnd.innerHeight;
    }-*/;


    // --------------------------------------------------------------------------------


    private enum Type {
        MARGIN(COLOUR_MARGIN),
        BORDER(COLOUR_BORDER),
        PADDING(COLOUR_PADDING),
        CONTENT(COLOUR_CONTENT);

        private final String colour;

        Type(final String colour) {
            this.colour = colour;
        }
    }


    // --------------------------------------------------------------------------------


    private static final class Edges {

        private final double top;
        private final double right;
        private final double bottom;
        private final double left;

        private Edges(final Element element, final String prefix, final String suffix) {
            top = getComputedPx(element, prefix + "Top" + suffix);
            right = getComputedPx(element, prefix + "Right" + suffix);
            bottom = getComputedPx(element, prefix + "Bottom" + suffix);
            left = getComputedPx(element, prefix + "Left" + suffix);
        }

        private double get(final String position) {
            switch (position) {
                case TOP:
                    return top;
                case RIGHT:
                    return right;
                case BOTTOM:
                    return bottom;
                case LEFT:
                    return left;
                default:
                    return 0;
            }
        }
    }


    // --------------------------------------------------------------------------------


    private static final class Measurements {

        private Edges margin;
        private Edges padding;
        private Edges border;
        private double top;
        private double left;
        private double bottom;
        private double right;
        private double width;
        private double height;
        private double extremityTop;
        private double extremityBottom;
        private double extremityLeft;
        private double extremityRight;
        private boolean alignLeft;
        private boolean alignTop;

        private double contentWidth() {
            return width - border.left - border.right - padding.left - padding.right;
        }

        private double contentHeight() {
            return height - padding.top - padding.bottom - border.top - border.bottom;
        }

        /// The x coordinate of a label position on the content box.
        private double positionX(final String position) {
            final double x = left + border.left + padding.left;
            switch (position) {
                case TOP:
                case BOTTOM:
                case CENTER:
                    return x + contentWidth() / 2;
                case RIGHT:
                    return x + contentWidth();
                default:
                    return x;
            }
        }

        /// The y coordinate of a label position on the content box.
        private double positionY(final String position) {
            final double y = top + border.top + padding.top;
            switch (position) {
                case RIGHT:
                case LEFT:
                case CENTER:
                    return y + contentHeight() / 2;
                case BOTTOM:
                    return y + contentHeight();
                default:
                    return y;
            }
        }
    }


    // --------------------------------------------------------------------------------


    private static final class Label {

        private final Type type;
        private final String position;
        private final String text;

        private Label(final Type type, final String position, final String text) {
            this.type = type;
            this.position = position;
            this.text = text;
        }
    }


    // --------------------------------------------------------------------------------


    private static final class Rect {

        private final double x;
        private final double y;
        private final double w;
        private final double h;

        private Rect(final double x, final double y, final double w, final double h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
