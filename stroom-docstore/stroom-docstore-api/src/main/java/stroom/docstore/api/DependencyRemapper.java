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

package stroom.docstore.api;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;

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
                switch (expressionItem) {
                    case final ExpressionOperator operator -> children.add(remapExpression(operator));
                    case final ExpressionTerm expressionTerm -> {
                        final ExpressionTerm termCopy = expressionTerm.copy()
                                .docRef(remap(expressionTerm.getDocRef()))
                                .build();
                        children.add(termCopy);
                    }
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
