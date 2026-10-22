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

import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.indexing.FieldPlan;

import java.util.Map;

/// The index plans draft_index_mapping drafted or amended in a session, by index name (the Stroom MCP server's
/// `tools/indexing.py` `drafted_plans`), for save_xslt to save the indexing XSLT of the plan kept for the session.
///
/// The indexing tools keep the plans; until they bind theirs (Guice's `OptionalBinder.setBinding()`), the binding is
/// [#NONE], which knows of none: save_xslt is then given the plan (`index_plan`) or saves from the plan kept with
/// the XSLT.
public interface DraftedIndexPlans {

    /// Knows of no drafted plan.
    DraftedIndexPlans NONE = session -> Map.of();

    /// @param session A session.
    /// @return The plans drafted or amended in it, by index name.
    Map<String, FieldPlan> drafted(ToolSession session);
}
