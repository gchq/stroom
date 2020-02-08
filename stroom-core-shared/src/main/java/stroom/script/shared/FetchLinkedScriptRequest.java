package stroom.script.shared;

import stroom.docref.DocRef;

import java.util.Set;

public class FetchLinkedScriptRequest {
    private DocRef script;
    private Set<DocRef> loadedScripts;

    public FetchLinkedScriptRequest() {
    }

    public FetchLinkedScriptRequest(final DocRef script, final Set<DocRef> loadedScripts) {
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
