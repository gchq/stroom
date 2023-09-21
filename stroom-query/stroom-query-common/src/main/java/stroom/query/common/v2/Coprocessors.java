package stroom.query.common.v2;

import stroom.docref.DocRef;
import stroom.expression.api.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;

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
