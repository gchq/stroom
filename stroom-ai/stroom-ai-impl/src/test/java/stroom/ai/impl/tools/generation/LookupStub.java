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

package stroom.ai.impl.tools.generation;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.om.StructuredQName;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.EmptySequence;
import net.sf.saxon.value.SequenceType;

import java.io.StringReader;
import java.util.Map;
import javax.xml.transform.stream.StreamSource;

/// `stroom:lookup(map, key)` for plain Saxon: the value a reference loader wrote for the map and key (its XML, by
/// `map + "\n" + key`), as a document, as Stroom gives a value holding elements; nothing for a key the map lacks.
final class LookupStub extends ExtensionFunctionDefinition {

    private final Map<String, String> values;

    /// @param values Each value's XML, by `map + "\n" + key`.
    LookupStub(final Map<String, String> values) {
        this.values = values;
    }

    @Override
    public StructuredQName getFunctionQName() {
        return new StructuredQName("stroom", "stroom", "lookup");
    }

    @Override
    public int getMinimumNumberOfArguments() {
        return 2;
    }

    @Override
    public int getMaximumNumberOfArguments() {
        return 2;
    }

    @Override
    public SequenceType[] getArgumentTypes() {
        return new SequenceType[]{SequenceType.OPTIONAL_STRING, SequenceType.OPTIONAL_STRING};
    }

    @Override
    public SequenceType getResultType(final SequenceType[] suppliedArgumentTypes) {
        return SequenceType.NODE_SEQUENCE;
    }

    @Override
    public ExtensionFunctionCall makeCallExpression() {
        return new ExtensionFunctionCall() {
            @Override
            public Sequence call(final XPathContext context, final Sequence[] arguments) throws XPathException {
                final String value = values.get(text(arguments[0]) + "\n" + text(arguments[1]));
                if (value == null) {
                    return EmptySequence.getInstance();
                }
                try {
                    return new Processor(context.getConfiguration()).newDocumentBuilder()
                            .build(new StreamSource(new StringReader(value))).getUnderlyingNode();
                } catch (final SaxonApiException e) {
                    throw new XPathException(e.getMessage());
                }
            }
        };
    }

    private static String text(final Sequence argument) throws XPathException {
        final Item item = argument.head();
        return item == null
                ? ""
                : item.getStringValue();
    }
}
