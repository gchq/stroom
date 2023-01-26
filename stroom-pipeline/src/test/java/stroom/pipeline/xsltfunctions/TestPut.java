package stroom.pipeline.xsltfunctions;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TestPut extends AbstractXsltFunctionTest<Put> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestPut.class);
    
    private TaskScopeMap taskScopeMap;
    private Put put;

    @BeforeEach
    void setUp() {
        taskScopeMap = new TaskScopeMap();
        put = new Put(taskScopeMap);
    }

    @Test
    void call() {
        final String key1 = "key1";
        final String val1 = "val1";
        final String key2 = "key2";
        final String val2 = "val2";
        final Sequence sequence1 = callFunctionWithSimpleArgs(key1, val1);

        Assertions.assertThat(sequence1)
                .isInstanceOf(EmptyAtomicSequence.class);

        final Sequence sequence2 = callFunctionWithSimpleArgs(key2, val2);

        Assertions.assertThat(sequence2)
                .isInstanceOf(EmptyAtomicSequence.class);

        Assertions.assertThat(taskScopeMap.get(key1))
                        .isEqualTo(val1);
        Assertions.assertThat(taskScopeMap.get(key2))
                .isEqualTo(val2);
    }

    @Override
    Put getXsltFunction() {
        return put;
    }

    @Override
    String getFunctionName() {
        return Put.FUNCTION_NAME;
    }
}
