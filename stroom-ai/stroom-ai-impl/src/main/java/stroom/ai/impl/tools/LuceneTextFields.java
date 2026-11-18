/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools;

import stroom.docref.DocRef;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.IndexResource;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.LinkedHashMap;
import java.util.Map;

/// A Lucene index's text fields and their analyzers (e.g. KEYWORD), for [MisleadingSearches]: a CONTAINS on a
/// keyword field, or a range on a text field, finds nothing through Stroom. Its query fields don't say which
/// analyzer a field has, so they are read from the index's own field list.
public class LuceneTextFields {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneTextFields.class);
    private static final int MAX_FIELDS = 2_000;

    private final Provider<IndexResource> indexResourceProvider;

    /// @param indexResourceProvider Lists an index's fields, as the user.
    @Inject
    LuceneTextFields(final Provider<IndexResource> indexResourceProvider) {
        this.indexResourceProvider = indexResourceProvider;
    }

    /// @param index A data source.
    /// @return Its text fields and their analyzers if it is a Lucene index (an empty analyzer where none is set);
    /// none for another data source, or when they can't be read.
    public Map<String, String> of(final DocRef index) {
        final Map<String, String> text = new LinkedHashMap<>();
        if (!MisleadingSearches.LUCENE.equals(MisleadingSearches.backendOf(index).orElse(null))) {
            return text;
        }
        try {
            final ResultPage<IndexFieldImpl> found = indexResourceProvider.get().findFields(new FindFieldCriteria(
                    new PageRequest(0, MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, index, null, null));
            for (final IndexFieldImpl field : NullSafe.list(NullSafe.get(found, ResultPage::getValues))) {
                if (field.getFldType() == FieldType.TEXT && NullSafe.isNonEmptyString(field.getFldName())) {
                    text.put(field.getFldName(), NullSafe.getOrElse(field.getAnalyzerType(), AnalyzerType::name,
                            ""));
                }
            }
        } catch (final RuntimeException e) {
            // A check on the searches, not a reason for them not to run.
            LOGGER.debug(() -> LogUtil.message("Text fields of {} not read: {}", index, e.getMessage()), e);
        }
        return text;
    }
}
