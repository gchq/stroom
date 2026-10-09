/*
 * Copyright 2021 Crown Copyright
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

package stroom.pipeline.xsltfunctions;

import stroom.floormap.shared.FloorMapGeometry;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.NumericValue;

import java.util.ArrayList;
import java.util.List;

// STROOMWORKS-LOCAL: KEEP LOCAL ON MERGE FROM master.
// Rewritten for FloorMap area tracking (commit subject "Area tracking"); a net reduction of
// ~58 lines against upstream. The containment algorithm here is the server-side half of
// stroom.floormap.shared.FloorMapGeometry and must stay in step with it - the client paints
// areas with fill-rule="evenodd" and this must agree, or what looks filled does not test as
// inside. Do not take upstream's version without re-checking FloorMapGeometry.
class PointIsInsideXYPolygon extends StroomExtensionFunctionCall {

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        try {

            final Double xPos = getSafeDouble(functionName, context, arguments, 0);
            final Double yPos = getSafeDouble(functionName, context, arguments, 1);
            final Double[] xPolyData = getSafeDoubleArray(functionName, context, arguments, 2);
            final Double[] yPolyData = getSafeDoubleArray(functionName, context, arguments, 3);
            final Point [] polyData = createPolygon(functionName, xPolyData, yPolyData);

            boolean inside = false;
            if (xPos != null && yPos != null) {
                inside = isPointInPolygon(new Point(xPos, yPos), polyData);
            }
            return BooleanValue.get(inside);

        } catch (final XPathException | RuntimeException e) {
            final StringBuilder sb = new StringBuilder();
            sb.append(e.getMessage());
            outputWarning(context, sb, e);
        }

        return BooleanValue.FALSE;
    }

    Point [] createPolygon(final String functionName, final Double[] xPolyData, final Double[] yPolyData) {
        if (xPolyData == null || xPolyData.length == 0) {
            throw new IllegalArgumentException("No x values for polygon" +
                    " in XSLT function " + functionName);
        }
        if (yPolyData == null || yPolyData.length == 0) {
            throw new IllegalArgumentException("No x values for polygon" +
                    " in XSLT function " + functionName);
        }
        if (xPolyData.length < 3) {
            throw new IllegalArgumentException("Too few points for polygon" +
                    " in XSLT function " + functionName);
        }
        if (xPolyData.length != yPolyData.length) {
            throw new IllegalArgumentException("Different numbers of x and y values for polygon provided to" +
                    " XSLT function " + functionName);
        }

        final Point[] result = new Point[xPolyData.length];

        for (int i = 0; i < result.length; i++) {
            result[i] = new Point(xPolyData[i], yPolyData[i]);
        }

        return result;
    }

    Double getSafeDouble(final String functionName,
                         final XPathContext context,
                         final Sequence[] arguments,
                         final int index) throws XPathException {
        Double value = null;
        final Sequence sequence = arguments[index];
        if (sequence != null) {
            final Item item = sequence.iterate().next();
            if (item instanceof NumericValue) {
                value = ((NumericValue) item).getDoubleValue();
            }
        }

        if (value == null) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Illegal non numeric argument found in function ");
            sb.append(functionName);
            sb.append("() at position ");
            sb.append(index);
            outputWarning(context, sb, null);
        }

        return value;
    }

    Double[] getSafeDoubleArray(final String functionName,
                                         final XPathContext context,
                                         final Sequence[] arguments,
                                         final int index) throws XPathException {
        final List<Double> result = new ArrayList<>();
        final Sequence sequence = arguments[index];
        if (sequence != null) {
            final SequenceIterator iterator = sequence.iterate();
            Item item;
            while ((item = iterator.next()) != null) {
                if (item instanceof NumericValue) {
                    result.add(((NumericValue) item).getDoubleValue());
                } else {
                    final StringBuilder sb = new StringBuilder();
                    sb.append("Illegal non numeric value in sequence provided to function ");
                    sb.append(functionName);
                    sb.append("() at position ");
                    sb.append(index);
                    outputWarning(context, sb, null);
                }
            }
        }

        return result.toArray(new Double[0]);
    }

    /**
     * Delegates to the shared {@link FloorMapGeometry#contains} so this XSLT
     * function and the FloorMap client's area containment use one algorithm —
     * an event tagged as inside an area at ingest agrees with what the map
     * shows the event inside of.
     */
    private boolean isPointInPolygon(final Point p, final Point[] polygon) {
        final double[][] vertices = new double[polygon.length][];
        for (int i = 0; i < polygon.length; i++) {
            vertices[i] = new double[]{polygon[i].x, polygon[i].y};
        }
        return FloorMapGeometry.contains(vertices, p.x, p.y);
    }

    static class Point {
        public Point(final double x, final double y) {
            this.x = x;
            this.y = y;
        }

        public final double x;
        public final double y;
    }
}
