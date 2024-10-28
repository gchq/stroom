/*
 * Copyright 2024 Crown Copyright
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

import stroom.data.store.api.DataService;
import stroom.pipeline.state.MetaHolder;
import stroom.util.shared.Severity;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import java.util.Map;

public class MetaAttribute extends StroomExtensionFunctionCall {

    private final MetaHolder metaHolder;
    private final DataService dataService;

    private Map<CIKey, String> metaAttributes = null;

    @Inject
    MetaAttribute(final MetaHolder metaHolder,
                  final DataService dataService) {
        this.metaHolder = metaHolder;
        this.dataService = dataService;
    }

    @Override
    protected Sequence call(final String functionName,
                            final XPathContext context,
                            final Sequence[] arguments) {
        String result = null;

        try {
            String key = null;
            try {
                key = getSafeString(functionName, context, arguments, 0);

                if (metaAttributes == null) {
                    metaAttributes = dataService.metaAttributes(metaHolder.getMeta().getId());
                }
                result = metaAttributes.get(CIKey.of(key));
            } catch (final XPathException | RuntimeException e) {
                outputWarning(context, new StringBuilder("Error fetching meta attribute for key '" + key + "'"), e);
            }
        } catch (final RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }
}
