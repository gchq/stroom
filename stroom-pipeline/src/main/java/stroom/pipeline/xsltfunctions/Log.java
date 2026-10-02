/*
 * Copyright 2016 Crown Copyright
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

import stroom.util.shared.Severity;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

@XsltFunctionDef(
        name = Log.FUNCTION_NAME,
        commonCategory = XsltFunctionCategory.PIPELINE,
        commonDescription = """
                Writes a message to the processing log. `ERROR` and `FATAL` messages can cause a `RecordOutputFilter` to
                omit the record.
                """,
        commonReturnType = XsltDataType.EMPTY_SEQUENCE,
        commonReturnDescription = "An empty sequence.",
        signatures = {
                @XsltFunctionSignature(
                        args = {
                                @XsltFunctionArg(
                                        name = "severity",
                                        description = "The message severity, such as `INFO`, `WARN`, `ERROR` or " +
                                                "`FATAL`.",
                                        argType = XsltDataType.STRING
                                ),
                                @XsltFunctionArg(
                                        name = "message",
                                        description = "The message to write.",
                                        argType = XsltDataType.STRING
                                )
                        }
                )
        })
class Log extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "log";

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        try {
            final String severity = getSafeString(functionName, context, arguments, 0);
            final String message = getSafeString(functionName, context, arguments, 1);

            final Severity sev = Severity.getSeverity(severity);
            if (sev == null) {
                log(context, Severity.ERROR, "Unknown severity specified in XSLT: " + severity, null);
            } else {
                log(context, sev, message, null);
            }
        } catch (final XPathException | RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return EmptyAtomicSequence.getInstance();
    }
}
