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

package stroom.ai.impl;

import stroom.docref.DocRef;
import stroom.processor.shared.ProcessorFilter;

import java.util.Optional;
import java.util.Set;

/// What [UserActions] needs to know about Stroom's own state, as the current user sees it.
public interface UserActionLookup {

    /// @param id A processor filter's id.
    /// @return The filter, if it exists, is not deleted, and the user may see it.
    Optional<ProcessorFilter> filter(int id);

    /// @param docRef A document.
    /// @return The document with its name as Stroom has it, if it exists and the user may see it.
    Optional<DocRef> find(DocRef docRef);

    /// @param docRef A document.
    /// @return The document's tags, empty if it has none or cannot be found.
    Set<String> tags(DocRef docRef);

    /// @param pipeline A pipeline.
    /// @return The names of the Elasticsearch indexes that the pipeline (or a pipeline it inherits from) writes
    /// to: the `indexName` of its Elastic indexing filters, as the pipeline that is lowest in the chain sets it, and
    /// those of the Elastic Index documents it names. A name may hold variables, e.g. `{year}`.
    Set<String> elasticIndexNames(DocRef pipeline);

    /// @param query A StroomQL query.
    /// @return The data source the query reads, if it names one that exists and the user may use (Use permission,
    /// as running the query needs). Empty otherwise, with no difference between the cases.
    Optional<DocRef> usableDataSource(String query);
}
