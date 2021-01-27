package stroom.docstore.api;

import stroom.docref.DocRef;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
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

    public ExpressionOperator remapExpression(final ExpressionOperator expressionOperator) {
        final ExpressionOperator.Builder builder = ExpressionOperator
                .builder()
                .enabled(expressionOperator.getEnabled())
                .op(expressionOperator.getOp());
        if (expressionOperator.getChildren() != null) {
            final List<ExpressionItem> children = new ArrayList<>();
            expressionOperator.getChildren().forEach(expressionItem -> {
                if (expressionItem instanceof ExpressionOperator) {
                    children.add(remapExpression((ExpressionOperator) expressionItem));
                } else if (expressionItem instanceof ExpressionTerm) {
                    final ExpressionTerm expressionTerm = (ExpressionTerm) expressionItem;
                    children.add(expressionTerm.copy().docRef(remap(expressionTerm.getDocRef())).build());
                }
            });
            builder.children(children);
        }
        return builder.build();
    }

    public Set<DocRef> getDependencies() {
        return dependencies;
    }

    public boolean isChanged() {
        return changed.get();
    }
}
