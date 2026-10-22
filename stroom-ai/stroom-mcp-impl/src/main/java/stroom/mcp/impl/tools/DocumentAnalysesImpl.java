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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.DocumentAnalyses;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.docref.DocRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// The parts of the Stroom MCP server's `describe_document` that `read_document` (`stroom-ai-impl`) gives from the
/// ported tools' helpers: an index doc's survey ([IndexSurveys]) and an XSLT's lost mapping ([XsltSaves]).
class DocumentAnalysesImpl implements DocumentAnalyses {

    /// The most documents the survey samples, as describe_document's did.
    static final int SURVEY_DOCUMENTS = 100;

    private final Provider<IndexSurveys> indexSurveys;
    private final Provider<XsltSaves> xsltSaves;

    /// @param indexSurveys Surveys an index through Stroom.
    /// @param xsltSaves    Says why an XSLT's kept mapping is gone.
    @Inject
    DocumentAnalysesImpl(final Provider<IndexSurveys> indexSurveys, final Provider<XsltSaves> xsltSaves) {
        this.indexSurveys = indexSurveys;
        this.xsltSaves = xsltSaves;
    }

    @Override
    public Map<String, Object> surveyIndex(final ToolCallContext context, final DocRef index) {
        // Without the index's totals: counting them reads every document through Stroom, minutes on a large index
        // (seen: three minutes before the agent could go on). write_documentation counts them.
        final Map<String, Object> survey = new LinkedHashMap<>(indexSurveys.get()
                .surveyIndex(context, index, SURVEY_DOCUMENTS, false));
        survey.remove("documents");
        if (survey.get("fed_by") instanceof final List<?> fedBy) {
            final List<Map<String, Object>> pipelines = new ArrayList<>();
            for (final Object pipeline : fedBy) {
                if (pipeline instanceof final Map<?, ?> map) {
                    final Map<String, Object> entry = new LinkedHashMap<>();
                    map.forEach((key, value) -> entry.put(String.valueOf(key), value));
                    entry.put("plan", hasPlan(map.get("plan")));
                    pipelines.add(entry);
                }
            }
            survey.put("fed_by", pipelines);
        }
        return survey;
    }

    @Override
    public Optional<String> lostMapping(final DocRef xslt, final String description) {
        return xsltSaves.get().lostMapping(xslt, description);
    }

    // Python's bool(plan): a plan that is there and not empty.
    private static boolean hasPlan(final Object plan) {
        if (plan instanceof final Map<?, ?> map) {
            return !map.isEmpty();
        }
        return plan != null;
    }
}
