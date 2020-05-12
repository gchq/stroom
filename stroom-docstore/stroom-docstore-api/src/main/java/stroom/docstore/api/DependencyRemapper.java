package stroom.docstore.api;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class DependencyRemapper {
    private final Map<DocRef, DocRef> remappings;
    private final Set<DocRef> dependencies;
    private final AtomicBoolean changed;

    public DependencyRemapper(final Map<DocRef, DocRef> remappings) {
        this.remappings = remappings;
        this.dependencies = new HashSet<>();
        this.changed = new AtomicBoolean();
    }

    public DependencyRemapper() {
        this.remappings = Collections.emptyMap();
        this.dependencies = new HashSet<>();
        this.changed = new AtomicBoolean();
    }

    public DocRef remap(final DocRef docRef) {
        final DocRef remap = remappings.getOrDefault(docRef, docRef);
        changed.compareAndSet(false, !Objects.equals(remap, docRef));
        if (remap != null) {
            dependencies.add(remap);
        }
        return remap;
    }

    public void remapExpression(final ExpressionOperator expressionOperator) {
        if (expressionOperator.getChildren() != null) {
            expressionOperator.getChildren().forEach(expressionItem -> {
                if (expressionItem instanceof ExpressionOperator) {
                    remapExpression((ExpressionOperator) expressionItem);
                } else if (expressionItem instanceof ExpressionTerm) {
                    final ExpressionTerm expressionTerm = (ExpressionTerm) expressionItem;
                    expressionTerm.setDocRef(remap(expressionTerm.getDocRef()));
                }
            });
        }
    }

    public Set<DocRef> getDependencies() {
        return dependencies;
    }

    public boolean isChanged() {
        return changed.get();
    }
}
