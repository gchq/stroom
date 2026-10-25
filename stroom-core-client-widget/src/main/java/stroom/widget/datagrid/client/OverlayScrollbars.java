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

package stroom.widget.datagrid.client;

import stroom.widget.datagrid.client.model.ScrollThumb;
import stroom.widget.util.client.CssProperties;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.NativeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;

/// The scrollbar thumbs [DataGrid] draws over its rows (design section 4.2).
///
/// The grid's scroller is a native one with its own scrollbars hidden, so wheel, touch, keyboard
/// and `scrollIntoView` scrolling all stay native. These thumbs show where the view is and can be
/// dragged; a press on a track pages by one view towards it. The vertical track starts below the
/// sticky header.
///
/// Where the browser supports CSS scroll-driven animations, the thumbs follow the scroll position
/// with no script, so [#update()] need only be called when the content or the grid's size changes,
/// and once a scroll has ended. Elsewhere it must be called as the grid scrolls.
final class OverlayScrollbars {

    private static final int MIN_THUMB_LENGTH = 28;
    private static final String ACTIVE_CLASS_NAME = "data-grid__thumb--active";
    private static final String SCROLL_TIMELINE_CLASS_NAME = "data-grid--scroll-timeline";
    // A track with nothing to scroll
    private static final String TRACK_HIDDEN_CLASS_NAME = "data-grid__track--hidden";
    private static final boolean SCROLL_TIMELINE = supportsScrollTimeline();

    private final Element root;

    private final Element scroller;
    private final IntSupplier headerHeight;
    private final Element vTrack;
    private final Element vThumb;
    private final Element hTrack;
    private final Element hThumb;
    private final List<JavaScriptObject> listeners = new ArrayList<>();

    // Drag state, set while a thumb is being dragged.
    private ScrollThumb dragThumb;
    private boolean dragVertical;
    private double dragStartPointer;
    private double dragStartPosition;

    /// Works the grid's tracks and thumbs (its template's).
    ///
    /// @param root         The grid's root element.
    /// @param scroller     The grid's scroller.
    /// @param vTrack       The vertical scrollbar's track.
    /// @param vThumb       Its thumb.
    /// @param hTrack       The horizontal scrollbar's track.
    /// @param hThumb       Its thumb.
    /// @param headerHeight Supplies the height in pixels of the sticky header.
    OverlayScrollbars(final Element root,
                      final Element scroller,
                      final Element vTrack,
                      final Element vThumb,
                      final Element hTrack,
                      final Element hThumb,
                      final IntSupplier headerHeight) {
        this.root = root;
        this.scroller = scroller;
        this.vTrack = vTrack;
        this.vThumb = vThumb;
        this.hTrack = hTrack;
        this.hThumb = hThumb;
        this.headerHeight = headerHeight;

        if (SCROLL_TIMELINE) {
            root.addClassName(SCROLL_TIMELINE_CLASS_NAME);
        }
    }

    /// @return Whether the thumbs follow scrolling by themselves, so [#update()] need not be called
    /// as the grid scrolls.
    boolean followsScroll() {
        return SCROLL_TIMELINE;
    }

    /// Start listening for pointer events. Call when the grid is attached.
    void attach() {
        listeners.add(DataGridDom.addListener(vTrack, "pointerdown", false, e -> onTrackPointerDown(e, true)));
        listeners.add(DataGridDom.addListener(hTrack, "pointerdown", false, e -> onTrackPointerDown(e, false)));
        listeners.add(DataGridDom.addListener(vThumb, "pointermove", false, this::onThumbPointerMove));
        listeners.add(DataGridDom.addListener(hThumb, "pointermove", false, this::onThumbPointerMove));
        listeners.add(DataGridDom.addListener(vThumb, "pointerup", false, this::onThumbPointerUp));
        listeners.add(DataGridDom.addListener(hThumb, "pointerup", false, this::onThumbPointerUp));
    }

    /// Stop listening for pointer events. Call when the grid is detached.
    void detach() {
        for (final JavaScriptObject handle : listeners) {
            DataGridDom.removeListener(handle);
        }
        listeners.clear();
        dragThumb = null;
    }

    /// @param element An element.
    /// @return Whether the element is part of the scrollbars.
    boolean contains(final Element element) {
        return vTrack.isOrHasChild(element) || hTrack.isOrHasChild(element);
    }

    /// Size and place the thumbs for the scroller's current size and scroll position.
    void update() {
        final ScrollThumb v = verticalThumb();
        final ScrollThumb h = horizontalThumb();

        CssProperties.set(vTrack, "--data-grid__v-track-top", headerHeight.getAsInt() + "px");
        setHidden(vTrack, !v.isVisible());
        CssProperties.set(vThumb, "--data-grid__v-thumb-length", v.getLength() + "px");

        setHidden(hTrack, !h.isVisible());
        CssProperties.set(hThumb, "--data-grid__h-thumb-length", h.getLength() + "px");

        if (SCROLL_TIMELINE) {
            // The scroll-driven animations move the thumbs from 0 to their travel.
            CssProperties.set(root, "--data-grid__v-travel", v.getTravel() + "px");
            CssProperties.set(root, "--data-grid__h-travel", h.getTravel() + "px");
        }
        // Also place the thumbs directly. A running scroll-driven animation overrides this, but
        // where animations are turned off (e.g. the test runner's screenshots) it is what shows.
        CssProperties.set(vThumb, "--data-grid__v-thumb-position", v.getPosition() + "px");
        CssProperties.set(hThumb, "--data-grid__h-thumb-position", h.getPosition() + "px");
    }

    private static void setHidden(final Element track, final boolean hidden) {
        if (hidden) {
            track.addClassName(TRACK_HIDDEN_CLASS_NAME);
        } else {
            track.removeClassName(TRACK_HIDDEN_CLASS_NAME);
        }
    }

    private ScrollThumb verticalThumb() {
        final int header = headerHeight.getAsInt();
        final int viewport = scroller.getClientHeight() - header;
        return ScrollThumb.compute(scroller.getScrollTop(),
                viewport,
                scroller.getScrollHeight() - header,
                viewport,
                MIN_THUMB_LENGTH);
    }

    private ScrollThumb horizontalThumb() {
        final int viewport = scroller.getClientWidth();
        return ScrollThumb.compute(scroller.getScrollLeft(),
                viewport,
                scroller.getScrollWidth(),
                viewport,
                MIN_THUMB_LENGTH);
    }

    private static native boolean supportsScrollTimeline() /*-{
        return !!($wnd.CSS && $wnd.CSS.supports && $wnd.CSS.supports("animation-timeline: scroll()")
            && $wnd.CSS.supports("timeline-scope: --a"));
    }-*/;

    private void onTrackPointerDown(final NativeEvent event, final boolean vertical) {
        event.preventDefault();
        event.stopPropagation();
        final Element target = Element.as(event.getEventTarget());
        final Element thumb = vertical
                ? vThumb
                : hThumb;
        if (thumb.isOrHasChild(target)) {
            dragVertical = vertical;
            dragThumb = vertical
                    ? verticalThumb()
                    : horizontalThumb();
            dragStartPointer = vertical
                    ? event.getClientY()
                    : event.getClientX();
            dragStartPosition = dragThumb.getPosition();
            thumb.addClassName(ACTIVE_CLASS_NAME);
            DataGridDom.setPointerCapture(thumb, DataGridDom.getPointerId(event));
        } else if (vertical) {
            // A press on the track pages by one view towards it.
            final int page = scroller.getClientHeight() - headerHeight.getAsInt();
            final boolean before = event.getClientY() < vThumb.getAbsoluteTop();
            scroller.setScrollTop(scroller.getScrollTop() + (before
                    ? -page
                    : page));
        } else {
            final int page = scroller.getClientWidth();
            final boolean before = event.getClientX() < hThumb.getAbsoluteLeft();
            scroller.setScrollLeft(scroller.getScrollLeft() + (before
                    ? -page
                    : page));
        }
    }

    private void onThumbPointerMove(final NativeEvent event) {
        if (dragThumb == null) {
            return;
        }
        final double pointer = dragVertical
                ? event.getClientY()
                : event.getClientX();
        final double scroll = dragThumb.scrollForPosition(dragStartPosition + pointer - dragStartPointer);
        if (dragVertical) {
            scroller.setScrollTop((int) Math.round(scroll));
        } else {
            scroller.setScrollLeft((int) Math.round(scroll));
        }
    }

    private void onThumbPointerUp(final NativeEvent event) {
        if (dragThumb != null) {
            final Element thumb = dragVertical
                    ? vThumb
                    : hThumb;
            thumb.removeClassName(ACTIVE_CLASS_NAME);
            DataGridDom.releasePointerCapture(thumb, DataGridDom.getPointerId(event));
            dragThumb = null;
        }
    }
}
