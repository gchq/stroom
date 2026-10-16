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

import stroom.pathways.shared.pathway.NodeUsage;
import stroom.pathways.shared.pathway.PathNode;
import stroom.pathways.shared.pathway.Pathway;

import java.util.Map;
import java.util.Set;

/**
 * What a drawing is made from: the model to show, where to put things, and what is known about how
 * each node has changed.
 */
class RenderRequest {

    private final Pathway pathway;
    private final PathNode layout;
    private final Map<String, NodeChange> changes;
    private final Map<String, NodeUsage> usage;
    private final long asAt;
    private final long changeCeiling;
    private final long usageCeiling;
    private final boolean legendVisible;
    private final Set<String> onPath;
    private final double zoom;

    RenderRequest(final Pathway pathway,
                  final PathNode layout,
                  final Map<String, NodeChange> changes,
                  final Map<String, NodeUsage> usage,
                  final long asAt,
                  final long changeCeiling,
                  final long usageCeiling,
                  final boolean legendVisible,
                  final Set<String> onPath,
                  final double zoom) {
        this.pathway = pathway;
        this.layout = layout;
        this.changes = changes;
        this.usage = usage;
        this.asAt = asAt;
        this.changeCeiling = changeCeiling;
        this.usageCeiling = usageCeiling;
        this.legendVisible = legendVisible;
        this.onPath = onPath;
        this.zoom = zoom;
    }

    /**
     * The nodes the path being looked at ran, by uuid, or empty where no path is. The rest are drawn
     * faintly — where they sit and what they join stay as they are, so two paths can be held against
     * one another without the drawing rearranging itself between them.
     */
    Set<String> getOnPath() {
        return onPath;
    }

    /**
     * The model to show.
     */
    Pathway getPathway() {
        return pathway;
    }

    /**
     * Every node the model has ever held, which is the set of nodes the drawing is made of rather
     * than only the ones being shown. Which of them the picture gives room to is a separate question,
     * answered by the model being shown — this is what says which elements exist at all, and keeping
     * that the same from one step to the next is what lets a step be eased rather than drawn again.
     *
     * <p>Nodes in here that the model being shown does not do — it has not learnt them yet, or the
     * work no longer does them — are kept but drawn at nothing, and placed on top of whatever they
     * hang off rather than given room of their own. So the picture holds only the work being done at
     * the moment being looked at, while the drawing holds the same nodes throughout and every step
     * through the history can be eased rather than drawn again.
     */
    PathNode getLayout() {
        return layout;
    }

    /**
     * How much each node had changed and when it last had, by node uuid, as at the moment being shown.
     */
    Map<String, NodeChange> getChanges() {
        return changes;
    }

    /**
     * How much each node had been used at the moment being shown, by node uuid. Taken from the reading
     * kept whenever a trace changed the model, because the node itself only knows how much it has been
     * used now — a node absent from this has no reading at or before the moment being shown.
     */
    Map<String, NodeUsage> getUsage() {
        return usage;
    }

    /**
     * The most any one node has ever changed, and the most any one node has ever been used. What the
     * sizes are measured against.
     *
     * <p>Taken over the whole history rather than over the part being shown. Measured against the
     * part being shown, the largest node of the moment would always be drawn at full size, so winding
     * back would rescale the picture rather than shrink it and nothing could be seen to grow.
     */
    long getChangeCeiling() {
        return changeCeiling;
    }

    long getUsageCeiling() {
        return usageCeiling;
    }

    /**
     * Whether the drawing shows what its colours and sizes mean. Asked for here rather than put on
     * afterwards, so the drawing owns all of its own markup and nothing has to reach into it.
     */
    boolean isLegendVisible() {
        return legendVisible;
    }

    /**
     * How far the drawing is being scaled to be seen, where 1 is the size it is drawn at. What the
     * drawing measures in its own units has to be held against this wherever the screen, rather than
     * the model, is what sets the limit.
     */
    double getZoom() {
        return zoom;
    }

    /**
     * The moment being looked at, which is the time of the change wound back to rather than the time
     * now. How long ago a node changed is measured from here.
     */
    long getAsAt() {
        return asAt;
    }
}
