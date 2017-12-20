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
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.value.StringValue;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.dictionary.shared.DictionaryService;
import stroom.dictionary.shared.FindDictionaryCriteria;
import stroom.entity.shared.BaseResultList;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

@Component
@Scope(value = StroomScope.TASK)
class Dictionary extends StroomExtensionFunctionCall {
    private final DictionaryService dictionaryService;

    private Map<String, String> cachedData;

    @Inject
    Dictionary(final DictionaryService dictionaryService) {
        this.dictionaryService = dictionaryService;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        String result = null;

        try {
            final String name = getSafeString(functionName, context, arguments, 0);
            if (name != null && name.length() > 0) {
                if (cachedData == null) {
                    cachedData = new HashMap<>();
                }

                if (cachedData.containsKey(name)) {
                    result = cachedData.get(name);

                } else {
                    try {
                        // Try and load a dictionary with the supplied name.
                        final FindDictionaryCriteria criteria = new FindDictionaryCriteria(name);
                        criteria.setSort(FindDictionaryCriteria.FIELD_ID);
                        final BaseResultList<stroom.dictionary.shared.Dictionary> list = dictionaryService
                                .find(criteria);

                        if (list == null || list.size() == 0) {
                            log(context, Severity.WARNING, "Dictionary not found with name '" + name
                                    + "'. You might not have permission to access this dictionary", null);
                        } else {
                            if (list.size() > 1) {
                                log(context, Severity.INFO, "Multiple dictionaries found with name '" + name
                                        + "' - using the first one that was created", null);
                            }

                            final stroom.dictionary.shared.Dictionary dictionary = list.getFirst();
                            result = dictionary.getData();
                        }
                    } catch (final Exception e) {
                        log(context, Severity.ERROR, e.getMessage(), e);
                    }

                    // Remember this data for the next call.
                    cachedData.put(name, result);
                }
            }
        } catch (final Exception e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }
}
