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

package stroom.query.language.functions;

import stroom.query.language.functions.ref.StoredValues;

import org.xml.sax.InputSource;

import java.io.StringReader;
import java.text.ParseException;
import java.util.function.Supplier;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

@SuppressWarnings("unused") //Used by FunctionFactory
@FunctionDef(
        name = XPath.NAME,
        commonCategory = FunctionCategory.STRING,
        commonReturnType = ValString.class,
        commonReturnDescription = "The string value of the matched node.",
        signatures = @FunctionSignature(
                description = "Extracts a value from an XML string using an XPath 1.0 expression.",
                args = {
                        @FunctionArg(
                                name = "xml",
                                description = "The XML string to evaluate.",
                                argType = ValString.class),
                        @FunctionArg(
                                name = "xpath",
                                description = "The XPath expression to use for extraction.",
                                argType = ValString.class)}))
class XPath extends AbstractManyChildFunction {

    static final String NAME = "xpath";
    private Generator gen;
    private boolean simple;
    private static final XPathFactory X_PATH_FACTORY = XPathFactory.newInstance();

    public XPath(final String name) {
        super(name, 2, 2);
    }

    @Override
    public void setParams(final Param[] params) throws ParseException {
        super.setParams(params);

        // See if this is a static computation.
        simple = true;
        for (final Param param : params) {
            if (!(param instanceof Val)) {
                simple = false;
                break;
            }
        }

        if (simple) {
            // Static computation.
            final String xml = params[0].toString();
            final String xpathPattern = params[1].toString();

            if (xpathPattern.isEmpty()) {
                gen = new StaticValueFunction(ValErr.create(
                        "An empty XPath expression has been defined for second argument of '" + name + "' function"))
                        .createGenerator();
            } else {
                try {
                    final javax.xml.xpath.XPath xpath = X_PATH_FACTORY.newXPath();
                    final XPathExpression expr = xpath.compile(xpathPattern);
                    try {
                        final String result = expr.evaluate(new InputSource(new StringReader(xml)));
                        gen = new StaticValueFunction(ValString.create(result)).createGenerator();
                    } catch (final Exception e) {
                        // If XML parsing fails, we'll handle it at runtime or just return a static ValErr.
                        gen = new StaticValueFunction(ValErr.create(e.getMessage())).createGenerator();
                    }
                } catch (final XPathExpressionException e) {
                    gen = new StaticValueFunction(ValErr.create("Error in XPath expression: " + e.getMessage()))
                            .createGenerator();
                }
            }

        } else {
            if (params[1] instanceof Val) {
                // Test XPath is valid.
                final String xpathPattern = params[1].toString();
                if (xpathPattern.isEmpty()) {
                    throw new ParseException(
                            "An empty XPath expression has been defined for second argument of '" + name + "' function", 0);
                }
                try {
                    X_PATH_FACTORY.newXPath().compile(xpathPattern);
                } catch (final XPathExpressionException e) {
                    throw new ParseException("Error in XPath expression: " + e.getMessage(), 0);
                }
            }
        }
    }

    @Override
    public Generator createGenerator() {
        if (gen != null) {
            return gen;
        }
        return super.createGenerator();
    }

    @Override
    protected Generator createGenerator(final Generator[] childGenerators) {
        return new Gen(childGenerators);
    }

    @Override
    public boolean hasAggregate() {
        if (simple) {
            return false;
        }
        return super.hasAggregate();
    }

    private static final class Gen extends AbstractManyChildGenerator {

        private XPathExpression staticExpr;

        Gen(final Generator[] childGenerators) {
            super(childGenerators);
            // If the XPath pattern is a constant, we can pre-compile it for this generator.
            if (childGenerators[1] instanceof StaticValueGen staticGen) {
                final String xpathPattern = staticGen.eval(null, null).toString();
                if (!xpathPattern.isEmpty()) {
                    try {
                        staticExpr = X_PATH_FACTORY.newXPath().compile(xpathPattern);
                    } catch (XPathExpressionException e) {
                        // Ignore and re-compile during eval if needed.
                    }
                }
            }
        }

        @Override
        public Val eval(final StoredValues storedValues, final Supplier<ChildData> childDataSupplier) {
            final Val valXml = childGenerators[0].eval(storedValues, childDataSupplier);
            if (!valXml.type().isValue()) {
                return valXml;
            }
            final Val valXPath = childGenerators[1].eval(storedValues, childDataSupplier);
            if (!valXPath.type().isValue()) {
                return ValErr.wrap(valXPath);
            }

            try {
                final String xml = valXml.toString();
                final String xpathPattern = valXPath.toString();
                
                XPathExpression expr = staticExpr;
                if (expr == null) {
                    expr = X_PATH_FACTORY.newXPath().compile(xpathPattern);
                }

                final String result = expr.evaluate(new InputSource(new StringReader(xml)));
                return ValString.create(result);

            } catch (final Exception e) {
                return ValErr.create(e.getMessage());
            }
        }
    }
}
