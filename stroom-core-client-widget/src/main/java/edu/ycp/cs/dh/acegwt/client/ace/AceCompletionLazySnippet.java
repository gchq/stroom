package edu.ycp.cs.dh.acegwt.client.ace;

import com.google.gwt.core.client.JavaScriptObject;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * STROOM ADDITION, ADDED BY AT055612 05/09/2023
 *
 * Allows us to have snippets that are created only as needed, useful for
 * when we have to hit the db to get descriptions for things like datasources.
 */
public class AceCompletionLazySnippet extends AceCompletion {

    private final Supplier<AceCompletionSnippet> snippetSupplier;
    private AceCompletionSnippet aceCompletionSnippet = null;

    public AceCompletionLazySnippet(final Supplier<AceCompletionSnippet> snippetSupplier) {
        this.snippetSupplier = Objects.requireNonNull(snippetSupplier);
    }

    @Override
    JavaScriptObject toJsObject() {
        if (aceCompletionSnippet == null) {
            aceCompletionSnippet = snippetSupplier.get();
        }
        return aceCompletionSnippet.toJsObject();
    }
}
