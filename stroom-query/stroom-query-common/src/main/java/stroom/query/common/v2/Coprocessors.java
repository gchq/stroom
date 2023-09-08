package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.dashboard.expression.v1.ref.ErrorConsumer;
import stroom.docref.DocRef;
import stroom.expression.api.ExpressionContext;

import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.util.Set;
import java.util.function.BiConsumer;

public interface Coprocessors extends ValuesConsumer {

    void readPayloads(Input input);

    void writePayloads(Output output);

    ErrorConsumer getErrorConsumer();

    ExpressionContext getExpressionContext();

    Coprocessor get(int coprocessorId);

    boolean isPresent();

    FieldIndex getFieldIndex();

    void forEachExtractionCoprocessor(BiConsumer<DocRef, Set<Coprocessor>> consumer);

    long getByteSize();
}
