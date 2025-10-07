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

import stroom.util.NullSafe;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Location;

import net.sf.saxon.expr.Expression;
import net.sf.saxon.expr.StaticContext;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.lib.ExtensionFunctionCall;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;

class ExtensionFunctionCallProxy extends ExtensionFunctionCall {

    private final String functionName;
    private transient StroomExtensionFunctionCall functionCall;
    private transient Location location = null;

    ExtensionFunctionCallProxy(final String functionName) {
        this.functionName = functionName;
    }

    @Override
    public Sequence call(final XPathContext context, final Sequence[] arguments) throws XPathException {
        return functionCall.call(functionName, context, arguments, location);
    }

    void setFunctionCall(final StroomExtensionFunctionCall functionCall) {
        this.functionCall = functionCall;
    }

    void clearFunctionCall() {
        this.functionCall = null;
    }

    @Override
    public void supplyStaticContext(final StaticContext context,
                                    final int locationId,
                                    final Expression[] arguments) {

        // Called before the func executes, but after setFunctionCall(), so hold the location locally
        // such that we can pass the location into the function when called.
        this.location = NullSafe.get(
                context,
                StaticContext::getContainingLocation,
                saxonLocation ->
                        DefaultLocation.of(saxonLocation.getLineNumber(), saxonLocation.getColumnNumber()));

//        LOGGER.trace("supplyStaticContext [{}({})] for {}({}), line: {}",
//                functionName,
//                System.identityHashCode(this),
//                NullSafe.get(functionCall, Object::getClass, Class::getSimpleName),
//                NullSafe.get(functionCall, System::identityHashCode),
//                NullSafe.get(location, Location::getLineNo));
    }
}
