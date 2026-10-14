/*
 * Copyright 2016-2026 Crown Copyright
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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;

/**
 * A tool that is always available to the "Ask Stroom AI" chat agent, without needing an external MCP
 * server. Add a new tool by implementing this interface and binding it in {@code AiModule} (see
 * {@code GuiceUtil.buildMultiBinder(binder(), BuiltInTool.class)}); {@link BuiltInTools} picks it up
 * automatically.
 */
public interface BuiltInTool {

    /**
     * @return the tool's name, description and parameter schema, as advertised to the model.
     */
    ToolSpecification specification();

    /**
     * Runs the tool.
     *
     * @param request the model's call, including its JSON {@link ToolExecutionRequest#arguments() arguments}
     * @return the result to feed back to the model. Return a plain explanatory string on bad input rather
     * than throwing - it is fed back to the model, which can correct itself.
     */
    String execute(ToolExecutionRequest request);

    /**
     * Runs the tool for a chat. Tools that work on the chat itself (e.g. its history) override this; others
     * need not.
     *
     * @param request the model's call
     * @param chatId  the chat the call is made in
     * @return the result to feed back to the model
     */
    default String execute(final ToolExecutionRequest request, final int chatId) {
        return execute(request);
    }

    /**
     * @return True if the tool only reads or works things out, and cannot change anything. Only read-only tools
     * may be used while the user is away (an unattended run). Defaults to true; override for any tool that
     * changes something.
     */
    default boolean isReadOnly() {
        return true;
    }

    /**
     * @return True if what the tool returns could hold instructions planted by someone other than the user (e.g.
     * tool output, workspace files or Stroom data). Once a run has used such a tool, changes it makes that outlive
     * the chat need the user's approval. Defaults to true; override only for tools whose output is known to be
     * safe.
     */
    default boolean readsUntrustedContent() {
        return true;
    }

    /**
     * @return True if the tool limits how much it returns itself (e.g. a page of a file), so its result is never
     * saved to the workspace in place of being returned, which would only make another file to read.
     */
    default boolean boundsItsOutput() {
        return false;
    }

    /**
     * @return True if the user must approve every call, even in a chat where they would not be asked, e.g. as the
     * tool sends data off the server. Defaults to false.
     */
    default boolean alwaysAsks() {
        return false;
    }
}
