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

package stroom.query.api.datasource;

import stroom.docref.DocRef;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;

public interface DataSourceProvider {

    /**
     * List the doc type that this data source can provide data for.
     */
    String getDataSourceType();

    /**
     * Get all of the docs that this data source provider can deliver data source info for.
     */
    List<DocRef> getDataSourceDocRefs();

    /**
     * Get field information for this data source.
     *
     * @param criteria Criteria to filter field information.
     * @return A result page of field information.
     */
    ResultPage<QueryField> getFieldInfo(FindFieldCriteria criteria);

    /**
     * Get a count of the number of fields that the specified data source can provide.
     *
     * @param docRef The document to count fields for.
     * @return A count of all of the fields for the provided document.
     */
    int getFieldCount(DocRef docRef);

    /**
     * Get documentation for the datasource referenced by the supplied doc ref.
     *
     * @param docRef The document to get documentation for.
     * @return An optional documentation string.
     */
    default Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.empty();
    }

    /**
     * Get a default extraction pipeline if there is one.
     *
     * @param docRef The document to get the default extraction pipeline for.
     * @return The default extraction pipeline.
     */
    default Optional<DocRef> fetchDefaultExtractionPipeline(final DocRef docRef) {
        return Optional.empty();
    }

    /**
     * Get the default time field for the specified doc.
     *
     * @param docRef The document to get the time field for.
     * @return The time field.
     */
    default Optional<QueryField> getTimeField(final DocRef docRef) {
        return Optional.empty();
    }
}
