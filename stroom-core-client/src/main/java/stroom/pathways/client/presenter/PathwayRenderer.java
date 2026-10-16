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

import com.google.gwt.dom.client.Element;
import com.google.gwt.safehtml.shared.SafeHtml;

/**
 * Draws a learnt model. One implementation per way of looking at it, chosen by the toolbar.
 *
 * <p>Two things every implementation owes the view around it, because everything but the drawing is
 * shared between them:
 *
 * <ul>
 *     <li>One outer element. It is what scrolls, and where the view was scrolled to is taken off it
 *     and put back when the model is redrawn.</li>
 *     <li>A {@code uuid} attribute on whatever element stands for a node. Clicking looks for the
 *     nearest parent carrying one, and picking the same node out again after the model is wound back
 *     searches on it. A node drawn without one cannot be selected.</li>
 * </ul>
 *
 * <p>The element carrying the uuid has a class put on and taken off it as it is selected, so it must
 * be an HTML element rather than an SVG one — {@code addClassName} does not work on SVG, where class
 * is an {@code SVGAnimatedString} rather than a string.
 */
interface PathwayRenderer {

    SafeHtml render(RenderRequest request);

    /**
     * Brings a drawing already on the screen up to date rather than building it again, and says
     * whether it managed to. A drawing remade flickers, loses where the reader had scrolled to and
     * starts its animation over, which is what makes stepping through a model's history hard to
     * watch; a drawing changed in place does none of that.
     *
     * <p>Answering false is always allowed and always safe: the caller builds the whole drawing
     * instead. A drawing that cannot usefully be changed in place simply never answers true.
     */
    default boolean update(final Element element, final RenderRequest request) {
        return false;
    }

    /**
     * Which part of the drawing the model is in, or null where that is the whole of it. What the view
     * fits to the panel and centres, rather than the drawing's own size — the two part company on a
     * drawing that keeps its box while the model within it shrinks.
     */
    default DrawnArea drawnArea() {
        return null;
    }

    /**
     * Brings the drawing up to date with how far it is being scaled, without drawing it again. Called
     * whenever the view's scale changes, which happens without the model changing at all.
     *
     * <p>For what a drawing measures in screen pixels rather than in its own units — a stroke thinner
     * than a pixel is drawn faint rather than thin, and vanishes in stretches.
     */
    default void rescale(final Element element, final double zoom) {
    }

    /**
     * Whether the drawing opens in the middle rather than at its top left. A drawing that puts the
     * root in the middle of the canvas shows nothing much in the corner the view would otherwise
     * start at.
     */
    boolean opensCentred();

    /**
     * Whether the drawing is moved around by dragging it. A drawing that reads top to bottom in rows
     * has nowhere to be moved to.
     */
    boolean isPannable();

    /**
     * Whether the drawing is scaled to be seen at a different size. Only a drawing built to be scaled
     * can be: the view resizes the box around it, and a drawing not laid out for that would be cut.
     */
    boolean isZoomable();

    /**
     * Whether the drawing says anything about how the model has changed, which is what decides
     * whether the changes behind it are worth asking the server for. They are a page of rows; a
     * drawing that does not use them should not be paying for them.
     */
    boolean usesHistory();

    /**
     * Acts on a click on one of the drawing's own buttons.
     *
     * @param id       the id of the element clicked, or of the nearest parent carrying one.
     * @param controls what the button can ask the view around the drawing to do.
     * @return whether the click was on one of this drawing's buttons, in which case it was not a
     * click on the drawing itself.
     */
    boolean onControl(String id, PathwayControls controls);
}
