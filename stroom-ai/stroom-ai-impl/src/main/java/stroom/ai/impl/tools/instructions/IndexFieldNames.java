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

package stroom.ai.impl.tools.instructions;

import stroom.ai.impl.tools.query.DataSourceAccess;
import stroom.docref.DocRef;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.LinkedHashSet;
import java.util.Set;

/// The fields of the data sources an AGENTS doc applies to, as the user can use them, so a proposed doc's
/// `## Fields` lines can be checked against them ([InstructionRules#fieldProblems]).
public class IndexFieldNames {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(IndexFieldNames.class);
    private static final int MAX_FIELDS = 2_000;

    private final DataSourceAccess dataSourceAccess;
    private final Provider<QueryService> queryServiceProvider;
    private final AgentInstructions agentInstructions;

    /// @param dataSourceAccess     Lists the data sources the user can use.
    /// @param queryServiceProvider Gives each data source's fields.
    /// @param agentInstructions    Gives the folder each is in.
    @Inject
    public IndexFieldNames(final DataSourceAccess dataSourceAccess,
                           final Provider<QueryService> queryServiceProvider,
                           final AgentInstructions agentInstructions) {
        this.dataSourceAccess = dataSourceAccess;
        this.queryServiceProvider = queryServiceProvider;
        this.agentInstructions = agentInstructions;
    }

    /// @param folderRef A folder, or the System root for everything.
    /// @return The fields of every data source the user can use in the folder or below it; empty when there are
    /// none, or they can't be read (so nothing is checked).
    public Set<String> under(final DocRef folderRef) {
        final Set<String> fields = new LinkedHashSet<>();
        try {
            final String folder = agentInstructions.folderPath(folderRef);
            for (final DocRef dataSource : dataSourceAccess.listUsable()) {
                final String in = agentInstructions.folderOf(dataSource);
                if ("/".equals(folder) || in.equals(folder) || in.startsWith(folder + "/")) {
                    final ResultPage<QueryField> page = queryServiceProvider.get().findFields(new FindFieldCriteria(
                            new PageRequest(0, MAX_FIELDS), FindFieldCriteria.DEFAULT_SORT_LIST, dataSource, null,
                            null));
                    NullSafe.list(NullSafe.get(page, ResultPage::getValues))
                            .forEach(field -> fields.add(field.getFldName()));
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to read the fields of the data sources under {}", folderRef, e);
            return Set.of();
        }
        return fields;
    }
}
