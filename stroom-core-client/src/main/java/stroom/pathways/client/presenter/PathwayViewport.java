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

package stroom.pathways.client.presenter;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.Style.Visibility;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.HTML;

import java.util.function.Supplier;

/**
 * How a drawing is looked at: how far it is scaled, where it is scrolled to, and moving it by hand.
 *
 * <p>Kept apart from what is drawn because the two have nothing to say to each other, and because
 * holding them together hid the ways they interfere. Scaling has to happen before the scroll is put
 * back, or the scroll lands outside a box that has not grown yet; putting the scroll back has to be
 * told apart from centring, or the two fight; and both have to wait for the browser to lay the
 * drawing out, which it does after the turn the drawing was made in. None of that was said anywhere
 * — it was a property of the order some lines happened to be written in.
 *
 * <p>Nothing here knows what a pathway is. It is told when a drawing is about to be replaced and when
 * it has been, and it is handed a way of finding the thing to centre on.
 */
class PathwayViewport {

    private static final double ZOOM_STEP = 1.25;
    private static final double MIN_ZOOM = 0.2;
    private static final double MAX_ZOOM = 3;
    // A little short of filling the panel, so the outermost nodes have somewhere to sit rather than
    // being cut in half by the edge.
    private static final double FIT_MARGIN = 0.95;
    // How many frames the drawing is held back waiting for the panel it goes in to have a size. A
    // handful: the panel is there on the next frame or two in practice, and a drawing kept off the
    // screen indefinitely would be worse than one in the wrong place.
    private static final int FIT_ATTEMPTS = 10;
    private static final String DRAGGING_CLASS = "pathway-dragging";
    // How far the pointer has to move before it counts as a drag rather than a click that wandered.
    private static final int DRAG_THRESHOLD = 3;

    private final HTML panel;
    // Where the drawing on show has put the model. Asked rather than given, because fitting is a
    // button press and the answer is different at every step of a replay — and because which drawing
    // is on show changes under this. Answering null means the whole drawing, which is what a drawing
    // that does not keep its box between steps always means.
    private Supplier<DrawnArea> area = () -> null;
    // Told whenever the drawing is scaled, so that whatever is drawn in screen pixels rather than in
    // the drawing's own units can be put right. Scaling does not redraw anything, so nothing else
    // would ever hear about it.
    private Runnable scaleHandler;

    private double zoom = 1;
    private int scrollLeft;
    private int scrollTop;
    // Where the drawing that is not on show was last left. The two drawings of a model are scrolled
    // differently — a tree is read from its top left and a graph from its middle — so each is given
    // back its own place rather than whatever the other happened to be showing.
    private int otherScrollLeft;
    private int otherScrollTop;
    private boolean swapping;
    // Whether the drawing being made is one the reader has not seen, and so opens showing all of
    // itself rather than at the size and place the one before it was left at.
    private boolean fitWanted;
    // Where the drawing was when it was last taken off the screen, which the browser does not keep.
    private int awayLeft;
    private int awayTop;
    private boolean away;
    // Centring is held as something wanted rather than done, because a drawing is replaced more than
    // once as a pathway opens and the request has to outlive the drawing it was made for.
    private boolean centreWanted;
    private boolean dragging;
    private boolean dragged;
    private int dragX;
    private int dragY;

    PathwayViewport(final HTML panel) {
        this.panel = panel;
        // Showing another tab takes this drawing off the screen altogether, and the browser keeps no
        // scroll for anything it has removed. Put back on the way in rather than redrawn: nothing
        // about the model has changed, only where it was hanging.
        panel.addAttachHandler(event -> {
            if (event.isAttached()) {
                restoreAfterAway();
            } else {
                rememberBeforeAway();
            }
        });
    }

    /**
     * Where to ask what part of the drawing the model is in, which is what fitting and centring work
     * from. Given once; which drawing is on show is the caller's business.
     */
    double scale() {
        return zoom;
    }

    void setScaleHandler(final Runnable scaleHandler) {
        this.scaleHandler = scaleHandler;
    }

    void setArea(final Supplier<DrawnArea> area) {
        this.area = area == null
                ? () -> null
                : area;
    }

    private void rememberBeforeAway() {
        final Element scroller = scroller();
        if (scroller != null) {
            awayLeft = scroller.getScrollLeft();
            awayTop = scroller.getScrollTop();
            away = true;
        }
    }

    private void restoreAfterAway() {
        if (!away) {
            return;
        }
        away = false;
        final int left = awayLeft;
        final int top = awayTop;
        // The drawing is put back on the screen before the browser has laid it out, so the scrollbars
        // have nowhere to go yet — and it is kept out of sight until they have, so that coming back to
        // a tab does not show the drawing at its top left before moving it.
        hide();
        Scheduler.get().scheduleDeferred(() -> {
            final Element scroller = scroller();
            if (scroller != null) {
                scroller.setScrollLeft(left);
                scroller.setScrollTop(top);
            }
            reveal();
        });
    }

    /**
     * The next drawing opens showing all of itself. For a different model, which may be a different
     * size altogether: the scale set for the last one says nothing about this one, and a reader handed
     * a model they have not seen wants to see what they have been given before anything else.
     *
     * <p>The size it is drawn at is set here as well, so that a drawing which cannot be measured when
     * the moment comes is shown as drawn rather than at whatever suited the model before it.
     */
    void fitOnNextDraw() {
        zoom = 1;
        fitWanted = true;
    }

    void zoomIn() {
        zoomAboutMiddle(ZOOM_STEP);
    }

    void zoomOut() {
        zoomAboutMiddle(1 / ZOOM_STEP);
    }

    /**
     * Scaled about a point on the screen rather than about the middle of the view, so that whatever
     * is under the pointer stays under it. What the wheel and a double click do: the reader is
     * pointing at the thing they want a closer look at, and holding the middle of a view they are not
     * looking at would send it out from under them and leave them chasing it.
     */
    void zoomIn(final int clientX, final int clientY) {
        zoomAbout(ZOOM_STEP, clientX, clientY);
    }

    void zoomOut(final int clientX, final int clientY) {
        zoomAbout(1 / ZOOM_STEP, clientX, clientY);
    }

    /**
     * The drawing being replaced is the other one, so the reader gets back where they left that one
     * rather than being carried to wherever this one happens to be. Takes effect on the next draw.
     */
    void swapDrawing() {
        swapping = true;
    }

    /**
     * Remembers where the drawing is scrolled to, before it is replaced by another.
     */
    void beforeDraw() {
        final Element scroller = scroller();
        final int left = scroller == null
                ? 0
                : scroller.getScrollLeft();
        final int top = scroller == null
                ? 0
                : scroller.getScrollTop();

        if (swapping) {
            swapping = false;
            // The two change places: what is on show now is what will be put back when it returns,
            // and what is arriving is given the place it was left at.
            scrollLeft = otherScrollLeft;
            scrollTop = otherScrollTop;
            otherScrollLeft = left;
            otherScrollTop = top;
        } else {
            scrollLeft = left;
            scrollTop = top;
        }
    }

    /**
     * @param keepScroll whether the drawing just made is the same model as the one it replaced, in
     *                   which case the reader is left where they were rather than moved.
     * @param centred    whether this drawing opens in the middle rather than at its top left.
     * @param centreOn   what to put in the middle, asked for only once there is something to measure.
     */
    void afterDraw(final boolean keepScroll,
                   final boolean centred,
                   final boolean zoomable,
                   final Supplier<Element> centreOn) {
        // Before the scroll is touched: the scrollbars only reach the scaled size once the box
        // carrying it has been resized.
        if (zoomable) {
            applyZoom();
        }

        final Element scroller = scroller();
        if (scroller != null) {
            if (keepScroll) {
                scroller.setScrollLeft(scrollLeft);
                scroller.setScrollTop(scrollTop);
            } else if (centred || (zoomable && fitWanted)) {
                centreWanted = true;
            } else {
                scroller.setScrollLeft(0);
                scroller.setScrollTop(0);
            }
        }
        if (centreWanted) {
            hide();
            // Only a drawing that can be scaled can be fitted; the rest are centred as before. The
            // asking is not cleared here: a drawing made before there is a panel to put it in cannot
            // be fitted yet, and what wants fitting has to outlive the attempt that could not.
            final boolean fit = zoomable && fitWanted;
            Scheduler.get().scheduleDeferred(() -> {
                if (fit) {
                    fitOrCentre(centreOn, FIT_ATTEMPTS);
                } else {
                    centre(centreOn);
                }
            });
        }
    }

    // Where a drawing has to be put somewhere before it is worth looking at. The browser paints what
    // was just drawn at the end of this turn, and where it belongs cannot be worked out until it has
    // done that — so a drawing left on show appears at its top left and then jumps, which on a large
    // model is two slow frames and reads as being drawn twice.
    private void hide() {
        panel.getElement().getStyle().setVisibility(Visibility.HIDDEN);
    }

    private void reveal() {
        panel.getElement().getStyle().clearVisibility();
    }

    /**
     * Puts the drawing back in the middle of a panel that has changed size under it. Where the middle
     * is has moved and the scroll the drawing was given has not, so it has to be worked out again.
     */
    void recentre(final Supplier<Element> centreOn) {
        centreWanted = true;
        hide();
        Scheduler.get().scheduleDeferred(() -> {
            // Fitted here where the drawing was made before there was anywhere to put it. A tab is
            // drawn and then opened, so the first attempt has no panel to measure and the asking
            // outlives it — this is the moment the panel turns up, and the same moment the reader
            // first sees the drawing.
            if (fitWanted) {
                fitOrCentre(centreOn, FIT_ATTEMPTS);
            } else {
                centre(centreOn);
            }
        });
    }

    boolean isDragging() {
        return dragging;
    }

    void startDrag(final int clientX, final int clientY) {
        dragX = clientX;
        dragY = clientY;
        dragging = true;
        dragged = false;
        Event.setCapture(panel.getElement());
        panel.addStyleName(DRAGGING_CLASS);
    }

    void drag(final int clientX, final int clientY) {
        final Element scroller = scroller();
        if (!dragging || scroller == null) {
            return;
        }

        // Dragging moves the drawing with the pointer, so the view moves the opposite way.
        final int dx = dragX - clientX;
        final int dy = dragY - clientY;
        if (Math.abs(dx) > DRAG_THRESHOLD || Math.abs(dy) > DRAG_THRESHOLD) {
            dragged = true;
        }
        scroller.setScrollLeft(scroller.getScrollLeft() + dx);
        scroller.setScrollTop(scroller.getScrollTop() + dy);
        dragX = clientX;
        dragY = clientY;
    }

    void endDrag() {
        if (dragging) {
            dragging = false;
            Event.releaseCapture(panel.getElement());
            panel.removeStyleName(DRAGGING_CLASS);
        }
    }

    /**
     * Whether the gesture that just ended moved the drawing, in which case the click it ends in is
     * not a click on anything. Answers once: asking clears it.
     */
    boolean wasDragged() {
        final boolean was = dragged;
        dragged = false;
        return was;
    }

    /**
     * The whole drawing at once: scaled to whichever of the panel's sides it runs out of first, and
     * put in the middle. Within the same limits as the buttons, so a model far too large to read is
     * shown as small as those will go rather than at a size nothing can be made out at.
     */
    boolean zoomToExtent() {
        final Element scroller = scroller();
        final Element canvas = canvas();
        if (scroller == null || canvas == null) {
            return false;
        }

        // What the model takes up, which is not always the whole drawing: a step taken in place keeps
        // the box the drawing already had, so a model that has shrunk since leaves room in it that
        // nothing is drawn in. Fitted to the box instead, a model that had shrunk would be shown too
        // small and pushed to whichever side of the room it happened to occupy.
        final DrawnArea drawn = area.get();
        final int width = drawn == null
                ? canvas.getOffsetWidth()
                : drawn.width();
        final int height = drawn == null
                ? canvas.getOffsetHeight()
                : drawn.height();
        final int across = scroller.getClientWidth();
        final int down = scroller.getClientHeight();
        if (width <= 0 || height <= 0 || across <= 0 || down <= 0) {
            // Nothing laid out to measure yet, and a size worked out from nothing would be no size at
            // all. Said so rather than waiting: a reader pressing the button can press it again, and a
            // drawing being opened is centred instead.
            return false;
        }

        // Never larger than the size it is drawn at. Fitting is about a drawing too big to see at
        // once; one that already fits is shown as drawn, in the middle, rather than blown up to fill
        // the panel — a model of a single node would otherwise arrive magnified as far as the zoom
        // goes, which says nothing about it and looks like a fault.
        zoom = Math.max(MIN_ZOOM, Math.min(1,
                FIT_MARGIN * Math.min(across / (double) width, down / (double) height)));
        applyZoom();
        // After the browser has taken the new size: the scrollbars only reach it once the box carrying
        // it has been laid out again, and the middle is worked out from how far they reach.
        Scheduler.get().scheduleDeferred(() -> centre(null));
        return true;
    }

    // Whatever was in the middle of the view stays in the middle of it. Scaling moves everything away
    // from the drawing's top left corner, so without an anchor the picture slides out from under the
    // reader towards the corner they are not looking at.
    private void zoomAboutMiddle(final double by) {
        final Element scroller = scroller();
        if (scroller == null) {
            zoom = held(zoom * by);
            applyZoom();
            return;
        }
        zoomAbout(by,
                left(scroller) + (scroller.getClientWidth() / 2),
                top(scroller) + (scroller.getClientHeight() / 2));
    }

    // clientX and clientY name the point on the screen that is to stay where it is.
    private void zoomAbout(final double by, final int clientX, final int clientY) {
        final double was = zoom;
        zoom = held(zoom * by);

        final Element scroller = scroller();
        if (scroller == null || was <= 0) {
            applyZoom();
            return;
        }

        // Where the point falls inside the view, and from that where it falls on the drawing, at the
        // size the drawing is made rather than the size it is shown at.
        // The room to spare is taken off before and put back after, so that what is worked out here is
        // a place on the drawing itself. Left in, it would be scaled along with everything else and
        // the view would slide sideways on every step of the zoom.
        final double viewX = clientX - left(scroller);
        final double viewY = clientY - top(scroller);
        final double atX = (scroller.getScrollLeft() + viewX - slackX()) / was;
        final double atY = (scroller.getScrollTop() + viewY - slackY()) / was;

        applyZoom();
        scroller.setScrollLeft((int) Math.round((atX * zoom) + slackX() - viewX));
        scroller.setScrollTop((int) Math.round((atY * zoom) + slackY() - viewY));
    }

    private static double held(final double zoom) {
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    // The element's edge in the same space a pointer is reported in, which is the window rather than
    // the page. What is held against the page carries the page's own scroll, so it is taken back off.
    private static int left(final Element element) {
        return element.getAbsoluteLeft() - element.getOwnerDocument().getScrollLeft();
    }

    private static int top(final Element element) {
        return element.getAbsoluteTop() - element.getOwnerDocument().getScrollTop();
    }

    private void applyZoom() {
        final Element canvas = canvas();
        if (canvas == null) {
            return;
        }

        // Read off the canvas rather than remembered, because scaling does not change what a thing
        // measures and this stays the drawing's own size however far it has been zoomed.
        final int width = canvas.getOffsetWidth();
        final int height = canvas.getOffsetHeight();
        if (width <= 0 || height <= 0) {
            // Nothing laid out to measure yet. Sizing the box to nothing would leave the drawing with
            // nowhere to scroll, so this waits and asks again rather than settling on zero.
            Scheduler.get().scheduleDeferred(this::applyZoom);
            return;
        }

        // A view's worth of room to spare, half of it each side, and the drawing moved over by that
        // half so it sits in the middle of it. Without this the box is exactly as big as the drawing,
        // so a drawing smaller than the view — which is what zooming out makes of any of them — has
        // nowhere to scroll to and is held in the top left corner whatever the reader does.
        canvas.getStyle().setProperty("transform",
                "translate(" + slackX() + "px, " + slackY() + "px) scale(" + zoom + ")");
        canvas.getParentElement().getStyle().setWidth((width * zoom) + (slackX() * 2), Unit.PX);
        canvas.getParentElement().getStyle().setHeight((height * zoom) + (slackY() * 2), Unit.PX);
        if (scaleHandler != null) {
            scaleHandler.run();
        }
    }

    // How far the drawing is moved over inside the box that holds it, which is half the room to spare
    // and so also the gap between the box's edge and the drawing's.
    private int slackX() {
        final Element scroller = scroller();
        return scroller == null
                ? 0
                : scroller.getClientWidth() / 2;
    }

    private int slackY() {
        final Element scroller = scroller();
        return scroller == null
                ? 0
                : scroller.getClientHeight() / 2;
    }

    // Fitted once there is something to fit it to, and centred as it stands if that never comes.
    //
    // A panel being opened is laid out a moment after what goes in it is drawn, so the first try has
    // nothing to measure. Giving up there puts the drawing on the screen at whatever size it happened
    // to be and then moves it the instant the panel has its own size, which is the flicker a reader
    // sees on opening a tab. Waiting instead costs a frame or two with the drawing still held back,
    // and it arrives in its place.
    private void fitOrCentre(final Supplier<Element> centreOn, final int attemptsLeft) {
        if (zoomToExtent()) {
            // Answered, so nothing asks again.
            fitWanted = false;
            return;
        }
        // Only where the panel is the thing that is missing. Anything else is not going to arrive by
        // waiting, and the reader would be left looking at nothing.
        final Element scroller = scroller();
        final boolean laidOut = scroller != null
                                && scroller.getClientWidth() > 0
                                && scroller.getClientHeight() > 0;
        if (!laidOut && attemptsLeft > 0) {
            Scheduler.get().scheduleDeferred(() -> fitOrCentre(centreOn, attemptsLeft - 1));
            return;
        }
        centre(centreOn);
    }

    private void centre(final Supplier<Element> centreOn) {
        final Element scroller = scroller();
        if (scroller == null || scroller.getClientWidth() <= 0) {
            // Nothing laid out to measure against yet. Still wanted, so the next draw tries again —
            // and there is nothing to keep out of sight in the meantime.
            reveal();
            return;
        }

        // On the thing asked for rather than on the middle of the drawing. The drawing is only as
        // large as what was placed in it, and a model that reaches further one way than another does
        // not put its root in the middle of that.
        //
        // Nothing asked for falls back to where the model came to, and only to the middle of the box
        // where the drawing cannot say. Centring on the box leaves a model that has shrunk sitting
        // off to one side of the room it was laid out in.
        final Element on = centreOn == null
                ? null
                : centreOn.get();
        final DrawnArea at = on == null
                ? area.get()
                : new DrawnArea(on.getOffsetLeft(), on.getOffsetTop(),
                        on.getOffsetWidth(), on.getOffsetHeight());
        if (at == null) {
            scroller.setScrollLeft((scroller.getScrollWidth() - scroller.getClientWidth()) / 2);
            scroller.setScrollTop((scroller.getScrollHeight() - scroller.getClientHeight()) / 2);
        } else {
            // Scaled, because what is measured here is what the drawing was laid out at rather than
            // what it is shown at.
            final double x = ((at.left() + (at.width() / 2.0)) * zoom) + slackX();
            final double y = ((at.top() + (at.height() / 2.0)) * zoom) + slackY();
            scroller.setScrollLeft((int) Math.round(x - (scroller.getClientWidth() / 2.0)));
            scroller.setScrollTop((int) Math.round(y - (scroller.getClientHeight() / 2.0)));
        }
        centreWanted = false;
        reveal();
    }

    // What scrolls is the drawing's own outer element, which the renderer promises is the only one.
    private Element scroller() {
        return panel.getElement().getFirstChildElement();
    }

    // What is scaled sits inside the box that carries the scaled size.
    private Element canvas() {
        final Element scroller = scroller();
        final Element sizer = scroller == null
                ? null
                : scroller.getFirstChildElement();
        return sizer == null
                ? null
                : sizer.getFirstChildElement();
    }
}
