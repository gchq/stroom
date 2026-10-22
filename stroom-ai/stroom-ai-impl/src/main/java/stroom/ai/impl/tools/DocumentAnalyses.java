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

import java.util.Map;
import java.util.Optional;

/// What `read_document` says of a document beyond its content, where the analysis lives with the tools ported from
/// the Stroom MCP server (`stroom-mcp-impl`), as its `describe_document` said it: a survey of what an index holds,
/// and why an XSLT's kept mapping is gone.
///
/// It is bound by the module that has them (`stroom-mcp-impl`); without that module, `read_document` gives neither.
/// Inject it as `Optional<DocumentAnalyses>` ([BuiltInToolsModule] declares it optional).
public interface DocumentAnalyses {

    /// What an Elastic Index or Lucene Index doc's index holds, read through Stroom: its fields, a sample of its
    /// documents (recent ones where there are any; how often each field is populated, sample values) and the
    /// pipelines that feed it. Not the whole index's totals: counting them reads every document through Stroom,
    /// minutes on a large index.
    ///
    /// @param context The call, told what the survey is doing.
    /// @param index   The index doc.
    /// @return The survey, as the Stroom MCP server's `describe_document` gave it (without the sampled documents
    /// themselves, and with whether each feeding pipeline keeps an index plan).
    /// @throws RuntimeException saying why, if the index can't be surveyed (e.g. its cluster is unreachable).
    Map<String, Object> surveyIndex(ToolCallContext context, DocRef index);

    /// Why an XSLT has no kept mapping when it should have one: it was saved with one, and none reads now (e.g. it
    /// was deleted from its Documentation tab).
    ///
    /// @param xslt        The XSLT.
    /// @param description Its description, where a kept mapping is kept.
    /// @return Why, and how to recover it; empty when it keeps one, or never did.
    Optional<String> lostMapping(DocRef xslt, String description);
}
