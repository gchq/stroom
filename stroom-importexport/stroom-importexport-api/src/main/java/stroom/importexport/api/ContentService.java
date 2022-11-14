/*
 * Copyright 2021 Crown Copyright
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

package stroom.importexport.api;

import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.importexport.shared.ImportState;
import stroom.util.shared.DocRefs;
import stroom.util.shared.QuickFilterResultPage;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import java.util.List;

public interface ContentService {

    ResourceKey performImport(final ResourceKey resourceKey,
                              final List<ImportState> confirmList);

    List<ImportState> confirmImport(final ResourceKey resourceKey,
                                    final List<ImportState> confirmList);

    ResourceGeneration exportContent(final DocRefs docRefs);

    QuickFilterResultPage<Dependency> fetchDependencies(final DependencyCriteria criteria);

    ResourceKey exportAll();

}
