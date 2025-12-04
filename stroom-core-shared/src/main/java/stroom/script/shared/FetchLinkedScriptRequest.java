/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.script.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Set;

@JsonPropertyOrder({"script", "loadedScripts"})
@JsonInclude(Include.NON_NULL)
public class FetchLinkedScriptRequest {

    @JsonProperty
    private DocRef script;
    @JsonProperty
    private Set<DocRef> loadedScripts;

    public FetchLinkedScriptRequest() {
    }

    @JsonCreator
    public FetchLinkedScriptRequest(@JsonProperty("script") final DocRef script,
                                    @JsonProperty("loadedScripts") final Set<DocRef> loadedScripts) {
        this.script = script;
        this.loadedScripts = loadedScripts;
    }

    public DocRef getScript() {
        return script;
    }

    public void setScript(final DocRef script) {
        this.script = script;
    }

    public Set<DocRef> getLoadedScripts() {
        return loadedScripts;
    }

    public void setLoadedScripts(final Set<DocRef> loadedScripts) {
        this.loadedScripts = loadedScripts;
    }
}
