/*
 * Copyright 2016-2026 Crown Copyright
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
import net.sf.saxon.value.DoubleValue;
import net.sf.saxon.value.NumericValue;

public class CosineSimilarity extends StroomExtensionMetaFunctionCall {

    public static final String FUNCTION_NAME = "cosine-similarity";

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments)
            throws XPathException {

        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        final SequenceIterator iteratorA = arguments[0].iterate();
        final SequenceIterator iteratorB = arguments[1].iterate();

        // Convert sequences to arrays first for safe iteration alignment
        final float[] a = toArray(iteratorA);
        final float[] b = toArray(iteratorB);

        if (a.length != b.length) {
            throw new XPathException("Embedding dimensions must match");
        }

        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0 || normB == 0) {
            return DoubleValue.ZERO;
        }

        return new DoubleValue(dot / (Math.sqrt(normA) * Math.sqrt(normB)));
    }

    private float[] toArray(final SequenceIterator iter) throws XPathException {
        float[] buffer = new float[256];
        int size = 0;

        while (true) {
            final Item item = iter.next();
            if (item == null) {
                break;
            }

            if (size == buffer.length) {
                final float[] newBuf = new float[buffer.length * 2];
                System.arraycopy(buffer, 0, newBuf, 0, buffer.length);
                buffer = newBuf;
            }

            buffer[size++] = ((NumericValue) item).getFloatValue();
        }

        final float[] result = new float[size];
        System.arraycopy(buffer, 0, result, 0, size);
        return result;
    }
}
