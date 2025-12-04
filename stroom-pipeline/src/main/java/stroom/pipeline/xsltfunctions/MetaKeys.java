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
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.StringValue;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

class MetaKeys extends StroomExtensionFunctionCall {

    private final MetaDataHolder metaDataHolder;

    @Inject
    MetaKeys(final MetaDataHolder metaDataHolder) {
        this.metaDataHolder = metaDataHolder;
    }

    @Override
    protected ArrayItem call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        Set<String> result = null;

        try {
            try {
                result = metaDataHolder.getMetaData().keySet();
            } catch (final RuntimeException e) {
                outputWarning(context, new StringBuilder("Error fetching meta keys"), e);
            }
        } catch (final RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return new SimpleArrayItem(new ArrayList<>());
        } else {
            return new SimpleArrayItem(result.stream()
                    .map(StringValue::makeStringValue)
                    .collect(Collectors.toList()));
        }
    }
}
