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

package stroom.pipeline.structure.client.component;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineElementType.Category;
import stroom.pipeline.structure.client.PipelineModel;
import stroom.util.shared.NullSafe;
import stroom.widget.htree.client.CanvasColours;
import stroom.widget.htree.client.ConnectorRenderer;
import stroom.widget.htree.client.treelayout.TreeLayout;

import com.google.gwt.canvas.dom.client.Context2d;
import com.google.gwt.canvas.dom.client.CssColor;

import java.util.function.Predicate;

/// Draws the pipeline tree's connectors as lines from the right of an element to the left of each
/// of its children, coloured by what flows along them: what the element they come from outputs
/// (see [#getFlow(PipelineElementType)]). The port dots at their ends are the boxes' own (see
/// [PipelineElementBox#setPorts]), so that they sit over the boxes' borders. The colours are the
/// theme's (`--pipeline-connector__*` in the theme's CSS), read when each connector is drawn, so
/// that they follow a change of theme; in forced colours they are the user's text colour. A
/// connector into a faded element is drawn on the faded context instead (one faded as a whole with
/// the faded elements' boxes, see [PipelineTreePanel]).
public class PipelineConnectorRenderer implements ConnectorRenderer<PipelineElement> {

    private static final double LINE_WIDTH = 2;
    private static final String FALLBACK_COLOUR = "grey";

    private final Context2d ctx;
    private final Context2d fadedCtx;
    private final Predicate<PipelineElement> faded;
    private final PipelineModel pipelineModel;

    /// @param ctx           The context to draw on.
    /// @param fadedCtx      The context to draw the connectors into faded elements on.
    /// @param faded         Whether an element is faded.
    /// @param pipelineModel The pipeline, for its elements' types.
    public PipelineConnectorRenderer(final Context2d ctx,
                                     final Context2d fadedCtx,
                                     final Predicate<PipelineElement> faded,
                                     final PipelineModel pipelineModel) {
        this.ctx = ctx;
        this.fadedCtx = fadedCtx;
        this.faded = faded;
        this.pipelineModel = pipelineModel;
    }

    @Override
    public void render(final TreeLayout<PipelineElement> treeLayout,
                       final double x1,
                       final double y1,
                       final double x2,
                       final double y2,
                       final boolean firstChild,
                       final boolean lastChild) {
        draw(ctx, Flow.RAW, x1, y1, x2, y2);
    }

    @Override
    public void render(final TreeLayout<PipelineElement> treeLayout,
                       final PipelineElement parent,
                       final PipelineElement child,
                       final double x1,
                       final double y1,
                       final double x2,
                       final double y2,
                       final boolean firstChild,
                       final boolean lastChild) {
        final Context2d context = faded.test(child)
                ? fadedCtx
                : ctx;
        draw(context, getFlow(pipelineModel.getElementType(parent)), x1, y1, x2, y2);
    }

    /// Gets what an element of a type outputs.
    ///
    /// @param elementType The element's type, or null for none.
    /// @return What flows from it: the raw stream from the source (which has no category) or a
    /// reader, XML events from a parser or filter, and bytes from a writer.
    public static Flow getFlow(final PipelineElementType elementType) {
        final Category category = NullSafe.get(elementType, PipelineElementType::getCategory);
        if (category == null) {
            return Flow.RAW;
        }
        return switch (category) {
            case PARSER, FILTER -> Flow.XML;
            case WRITER, DESTINATION -> Flow.BYTES;
            default -> Flow.RAW;
        };
    }

    private static void draw(final Context2d ctx,
                             final Flow flow,
                             final double x1,
                             final double y1,
                             final double x2,
                             final double y2) {
        // In forced colours (e.g. Windows High Contrast), which don't change what a canvas draws, the
        // user's text colour (the canvas's own colour, which they set)
        final String colour = CanvasColours.isForcedColours()
                ? CanvasColours.getTextColour(ctx.getCanvas())
                : CanvasColours.getProperty(ctx.getCanvas(), flow.getProperty());
        final CssColor cssColour = CssColor.make(NullSafe.isBlankString(colour)
                ? FALLBACK_COLOUR
                : colour);
        final double midX = x1 + ((x2 - x1) / 2);

        ctx.beginPath();
        ctx.moveTo(x1, y1);
        ctx.bezierCurveTo(midX, y1, midX, y2, x2, y2);
        ctx.setLineWidth(LINE_WIDTH);
        ctx.setStrokeStyle(cssColour);
        ctx.stroke();
    }



    // --------------------------------------------------------------------------------


    /// What flows along a connector: its name (e.g. for a CSS class) and the CSS property of its
    /// colour.
    public enum Flow {
        RAW("raw"),
        XML("xml"),
        BYTES("bytes");

        private final String name;

        Flow(final String name) {
            this.name = name;
        }

        /// @return Its name, e.g. `xml`.
        public String getName() {
            return name;
        }

        String getProperty() {
            return "--pipeline-connector__" + name;
        }
    }
}
