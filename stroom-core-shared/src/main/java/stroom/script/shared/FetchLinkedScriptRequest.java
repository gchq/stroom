package stroom.script.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.DocRef;

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
