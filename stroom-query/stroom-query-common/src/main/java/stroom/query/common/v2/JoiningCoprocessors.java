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

package stroom.query.common.v2;

import stroom.docref.DocRef;
import stroom.query.api.Query;
import stroom.query.language.JoinWhereSplitter;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;

/// Coprocessors that apply a query's joins to each row before passing it to the coprocessors that
/// evaluate, filter and group it.
///
/// A data source's search gives its rows to these coprocessors as it would to any. For each row,
/// fields named with the first source's alias, as in `logon.UserId`, are first given the values of
/// the fields they name, as in `UserId`. Then each join line is applied in order, each passing on
/// zero, one or several rows to the next, and the rows the last one passes on go to the wrapped
/// coprocessors.
///
/// Create these once the wrapped coprocessors exist and before the search starts, as creating them
/// adds the fields the first source's search must extract to the field index.
public final class JoiningCoprocessors implements Coprocessors {

    private final Coprocessors delegate;
    private final List<JoinProbe> probes;
    // Pairs of positions: the value at the second of each pair is copied to the first.
    private final int[] aliasCopies;

    private JoiningCoprocessors(final Coprocessors delegate,
                                final List<JoinProbe> probes,
                                final int[] aliasCopies) {
        this.delegate = delegate;
        this.probes = probes;
        this.aliasCopies = aliasCopies;
    }

    /// Wraps a query's coprocessors with its joins.
    ///
    /// @param delegate The coprocessors that evaluate, filter and group the joined rows.
    /// @param query    The query, for its first source's alias.
    /// @param plans    The plans of the query's join lines, made with the delegate's field index.
    /// @param tables   The complete join table of each plan, in the same order.
    /// @return The coprocessors to give the first source's rows to.
    public static JoiningCoprocessors create(final Coprocessors delegate,
                                             final Query query,
                                             final List<JoinPlan> plans,
                                             final List<JoinTable> tables) {
        Objects.requireNonNull(delegate);
        if (plans.size() != tables.size()) {
            throw new IllegalArgumentException("There must be one join table for each join plan");
        }
        final FieldIndex fieldIndex = delegate.getFieldIndex();
        final int[] aliasCopies = createAliasCopies(query, fieldIndex);
        final List<JoinProbe> probes = new ArrayList<>(plans.size());
        for (int i = 0; i < plans.size(); i++) {
            probes.add(new JoinProbe(plans.get(i), tables.get(i), fieldIndex, delegate.getErrorConsumer()));
        }
        return new JoiningCoprocessors(delegate, List.copyOf(probes), aliasCopies);
    }

    // For each field named with the first source's alias, the position of the field it names, which
    // is added to the field index so the search extracts it.
    private static int[] createAliasCopies(final Query query, final FieldIndex fieldIndex) {
        final String alias = query.getAlias();
        if (alias == null) {
            return new int[0];
        }
        final List<String> aliases = JoinPlan.getAliases(query);
        final List<Integer> copies = new ArrayList<>();
        for (final String field : fieldIndex.getFields()) {
            if (alias.equals(JoinWhereSplitter.findAlias(field, aliases))) {
                copies.add(fieldIndex.getPos(field));
                copies.add(fieldIndex.create(field.substring(alias.length() + 1)));
            }
        }
        return copies.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public void accept(final Val[] values) {
        applyJoins(values, delegate);
    }

    // Copies the values of fields named with the first source's alias, then applies each join line
    // in order, passing the rows the last one passes on to the consumer.
    private void applyJoins(final Val[] values, final ValuesConsumer consumer) {
        Val[] row = values;
        if (aliasCopies.length > 0) {
            row = Arrays.copyOf(values, Math.max(values.length, getFieldIndex().size()));
            for (int i = 0; i < aliasCopies.length; i += 2) {
                row[aliasCopies[i]] = row[aliasCopies[i + 1]];
            }
        }
        applyJoin(0, row, consumer);
    }

    // Applies the join line at the index, then each later one, to a row.
    private void applyJoin(final int index, final Val[] row, final ValuesConsumer consumer) {
        if (index == probes.size()) {
            consumer.accept(row);
        } else {
            probes.get(index).probe(row, joined -> applyJoin(index + 1, joined, consumer));
        }
    }

    @Override
    public void readPayloads(final Input input) {
        delegate.readPayloads(input);
    }

    @Override
    public void writePayloads(final Output output) {
        delegate.writePayloads(output);
    }

    @Override
    public ErrorConsumer getErrorConsumer() {
        return delegate.getErrorConsumer();
    }

    @Override
    public ExpressionContext getExpressionContext() {
        return delegate.getExpressionContext();
    }

    @Override
    public Coprocessor get(final int coprocessorId) {
        return delegate.get(coprocessorId);
    }

    @Override
    public boolean isPresent() {
        return delegate.isPresent();
    }

    @Override
    public FieldIndex getFieldIndex() {
        return delegate.getFieldIndex();
    }

    /// Gives each group of coprocessors that share an extraction pipeline as one coprocessor that
    /// applies the joins first, as an extracting search gives its rows to these groups rather than to
    /// [#accept(Val[])].
    ///
    /// @param consumer Takes each extraction pipeline and its coprocessor.
    @Override
    public void forEachExtractionCoprocessor(final BiConsumer<DocRef, Set<Coprocessor>> consumer) {
        delegate.forEachExtractionCoprocessor((docRef, coprocessors) ->
                consumer.accept(docRef, Set.of(new JoiningCoprocessor(coprocessors))));
    }

    @Override
    public long getByteSize() {
        return delegate.getByteSize();
    }


    // --------------------------------------------------------------------------------


    // A group of coprocessors sharing an extraction pipeline, given rows only once the joins are
    // applied. Only rows pass through it; the group's own coprocessors are cleared and give their
    // payloads through the wrapped coprocessors.
    private final class JoiningCoprocessor implements Coprocessor {

        private final Set<Coprocessor> coprocessors;

        private JoiningCoprocessor(final Set<Coprocessor> coprocessors) {
            this.coprocessors = coprocessors;
        }

        @Override
        public void accept(final Val[] values) {
            applyJoins(values, row -> coprocessors.forEach(coprocessor -> coprocessor.accept(row)));
        }

        @Override
        public ErrorConsumer getErrorConsumer() {
            return delegate.getErrorConsumer();
        }

        @Override
        public void clear() {
            coprocessors.forEach(Coprocessor::clear);
        }

        @Override
        public void readPayload(final Input input) {
            throw new UnsupportedOperationException("Payloads are read through the wrapped coprocessors");
        }

        @Override
        public void writePayload(final Output output) {
            throw new UnsupportedOperationException("Payloads are written through the wrapped coprocessors");
        }

        @Override
        public long getByteSize() {
            return coprocessors.stream().mapToLong(Coprocessor::getByteSize).sum();
        }
    }
}
