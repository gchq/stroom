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

import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.state.LocationHolder;
import stroom.pipeline.state.LocationHolder.FunctionType;
import stroom.util.shared.Severity;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.StringValue;

abstract class AbstractLocationFunction extends StroomExtensionFunctionCall {

    private final LocationHolder locationHolder;

    AbstractLocationFunction(final LocationHolder locationHolder) {
        this.locationHolder = locationHolder;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        String result = null;

        try {
            locationHolder.move(getType());
            final SourceLocation currentLocation = locationHolder.getCurrentLocation();
            if (currentLocation != null) {
                result = getValue(currentLocation);
            }
        } catch (final Exception e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }

    abstract String getValue(SourceLocation currentLocation);

    abstract FunctionType getType();
}
