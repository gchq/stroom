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
}
