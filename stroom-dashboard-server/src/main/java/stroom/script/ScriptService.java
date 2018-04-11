/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.script;

import stroom.entity.DocumentEntityService;
import stroom.entity.FindService;
import stroom.script.shared.FindScriptCriteria;
import stroom.script.shared.Script;

import java.util.Set;

public interface ScriptService extends DocumentEntityService<Script>, FindService<Script, FindScriptCriteria> {
    // TODO : Remove this when IFrames are able to pass user tokens so that script can be loaded in the context of the current user.
    Script loadByUuidInsecure(String uuid, Set<String> fetchSet);
}
