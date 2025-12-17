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

import stroom.pipeline.state.MetaDataHolder;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;

class AddMeta extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "add-meta";

    private final MetaDataHolder metaDataHolder;

    @Inject
    AddMeta(final MetaDataHolder metaDataHolder) {
        this.metaDataHolder = metaDataHolder;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        try {
            final String key = getSafeString(functionName, context, arguments, 0);
            final String value = getSafeString(functionName, context, arguments, 1);

            if (key == null || key.trim().isEmpty()) {
                return EmptyAtomicSequence.getInstance();
            }

            metaDataHolder.getMetaData().put(key, value == null ? "" : value);

        } catch (final Exception e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return EmptyAtomicSequence.getInstance();
    }
}
