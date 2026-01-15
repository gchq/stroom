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

import stroom.util.net.IpAddressUtil;
import stroom.util.shared.Severity;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import java.net.UnknownHostException;

class NumericIP extends StroomExtensionFunctionCall {

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {

        try {
            final String ipAddress = getSafeString(functionName, context, arguments, 0);
            try {
                return StringValue.makeStringValue(Long.toString(IpAddressUtil.toNumericIpAddress(ipAddress)));
            } catch (final RuntimeException e) {
                final StringBuilder sb = new StringBuilder();
                sb.append(e.getMessage());
                outputWarning(context, sb, e);
            }
        } catch (final XPathException | UnknownHostException | RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return EmptyAtomicSequence.getInstance();
    }
}
