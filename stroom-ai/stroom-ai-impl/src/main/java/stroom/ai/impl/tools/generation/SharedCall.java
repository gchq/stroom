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

package stroom.ai.impl.tools.generation;

import java.util.LinkedHashMap;
import java.util.Map;

/// A named template of an imported XSLT, called in place by a rule (a mapping's shared entry), as [XsltReader]
/// reads it.
///
/// @param path     Where it is called: the element it writes goes below this.
/// @param template The template's name.
/// @param params   What the call passes, by parameter name.
public record SharedCall(String path, String template, Map<String, String> params) {

    /// @return It as a map, keys in the order the reader fixtures pin: path, template, params.
    public Map<String, Object> toMap() {
        final Map<String, Object> out = new LinkedHashMap<>();
        out.put("path", path);
        out.put("template", template);
        out.put("params", params);
        return out;
    }
}
