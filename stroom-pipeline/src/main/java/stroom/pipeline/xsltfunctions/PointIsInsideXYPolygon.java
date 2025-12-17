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

package stroom.pipeline.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.SequenceIterator;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import net.sf.saxon.value.NumericValue;

import java.util.ArrayList;
import java.util.List;

class PointIsInsideXYPolygon extends StroomExtensionFunctionCall {

    static final String DELIMITER = ",";

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
            if (item != null && item instanceof NumericValue) {
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

    Double[] getSafeDelimitedDoubleArray(final String functionName,
                         final String delimiter,
                         final XPathContext context,
                         final Sequence[] arguments,
                         final int index) throws XPathException {
        String string = null;
        final Sequence sequence = arguments[index];
        if (sequence != null) {
            final Item item = sequence.iterate().next();
            if (item != null) {
                string = item.getStringValue();
            }
        }

        if (string == null) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Illegal non string argument found in function ");
            sb.append(functionName);
            sb.append("() at position ");
            sb.append(index);
            outputWarning(context, sb, null);
        }

        final String[] tokens = string.split(delimiter);
        final Double[] result = new Double[tokens.length];
        for (int i = 0; i < tokens.length; i++) {
            try {
                result[i] = Double.parseDouble(tokens[i]);
            } catch (final NumberFormatException ex) {
                final StringBuilder sb = new StringBuilder();
                sb.append("Illegal non numeric token found in delimited string provided to function ");
                sb.append(functionName);
                sb.append("() at position ");
                sb.append(index);
                outputWarning(context, sb, null);
                return null;
            }
        }

        return result;
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
                if (item != null && item instanceof NumericValue) {
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

        return result.toArray(new Double[result.size()]);
    }

    //Attribution: https://stackoverflow.com/questions/217578/how-can-i-determine-whether-a-2d-point-is-within-a-polygon
    private boolean isPointInPolygon(final Point p, final Point[] polygon) {
        double minX = polygon[ 0 ].x;
        double maxX = polygon[ 0 ].x;
        double minY = polygon[ 0 ].y;
        double maxY = polygon[ 0 ].y;
        for (int i = 1; i < polygon.length; i++) {
            final Point q = polygon[i];
            minX = Math.min(q.x, minX);
            maxX = Math.max(q.x, maxX);
            minY = Math.min(q.y, minY);
            maxY = Math.max(q.y, maxY);
        }

        if (p.x < minX || p.x > maxX || p.y < minY || p.y > maxY) {
            return false;
        }

        boolean inside = false;
        for (int i = 0, j = polygon.length - 1; i < polygon.length; j = i++) {
            if ((polygon[ i ].y > p.y) != (polygon[ j ].y > p.y) &&
                    p.x < (polygon[ j ].x - polygon[ i ].x) * (p.y - polygon[ i ].y) /
                            (polygon[ j ].y - polygon[ i ].y) + polygon[ i ].x) {
                inside = !inside;
            }
        }

        return inside;
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
