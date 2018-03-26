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

package stroom.pipeline.server.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;
import stroom.pipeline.server.LocationFactory;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;

import java.util.List;

abstract class StroomExtensionFunctionCall {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(StroomExtensionFunctionCall.class);

    private ErrorReceiver errorReceiver;
    private LocationFactory locationFactory;
    private List<PipelineReference> pipelineReferences;

    abstract Sequence call(String functionName, XPathContext context, Sequence[] arguments)
            throws XPathException;

    String getSafeString(final String functionName, final XPathContext context, final Sequence[] arguments, final int index) throws XPathException {
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

        return string;
    }

    Boolean getSafeBoolean(final String functionName, final XPathContext context, final Sequence[] arguments, final int index) throws XPathException {
        Boolean bool = null;
        final Sequence sequence = arguments[index];
        if (sequence != null) {
            final Item item = sequence.iterate().next();
            if (item != null && item instanceof BooleanValue) {
                bool = ((BooleanValue) item).getBooleanValue();
            }
        }

        if (bool == null) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Illegal non boolean argument found in function ");
            sb.append(functionName);
            sb.append("() at position ");
            sb.append(index);
            outputWarning(context, sb, null);
        }

        return bool;
    }

    void outputWarning(final XPathContext context, final StringBuilder msg, final Throwable e) {
        // Tell the logger.
        LOGGER.debug(msg, e);

        if (e != null) {
            msg.append(' ');
            msg.append(e.getMessage());
        }

        log(context, Severity.WARNING, msg.toString(), e);
    }

    void log(final XPathContext context, final Severity severity, final String message, final Throwable e) {
        final Location location = getLocation(context);
        errorReceiver.log(severity, location, getClass().getSimpleName(), message, e);
    }

    Location getLocation(final XPathContext context) {
        final Item item = context.getContextItem();
        if (item instanceof NodeInfo) {
            final NodeInfo nodeInfo = (NodeInfo) item;
            return locationFactory.create(nodeInfo.getLineNumber(), nodeInfo.getColumnNumber());
        }

        return locationFactory.create();
    }

    void configure(final ErrorReceiver errorReceiver,
                   final LocationFactory locationFactory,
                   final List<PipelineReference> pipelineReferences) {
        this.errorReceiver = errorReceiver;
        this.locationFactory = locationFactory;
        this.pipelineReferences = pipelineReferences;
    }

    ErrorReceiver getErrorReceiver() {
        return errorReceiver;
    }

    List<PipelineReference> getPipelineReferences() {
        return pipelineReferences;
    }
}
