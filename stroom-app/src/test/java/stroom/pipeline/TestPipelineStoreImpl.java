package stroom.pipeline;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.common.TestUtil;

import com.google.inject.TypeLiteral;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TestPipelineStoreImpl extends AbstractCoreIntegrationTest {

    protected static final String PIPE_1_NAME = "Pipe 1 a";
    protected static final String PIPE_2_NAME = "Pipe 2 aa";
    protected static final String PIPE_3_NAME = "Pipe 3 aaa";

    DocRef pipe1;
    DocRef pipe2;
    DocRef pipe3;

    @Inject
    private PipelineStore pipelineStore;

    @BeforeEach
    void setUp() {
        pipe1 = create(PIPE_1_NAME);
        pipe2 = create(PIPE_2_NAME);
        pipe3 = create(PIPE_3_NAME);
    }

    private DocRef create(final String name) {
        PipelineDoc pipe1 = pipelineStore.createDocument();
        pipe1.setName(name);
        return pipelineStore.writeDocument(pipe1).asDocRef();
    }

    @TestFactory
    Stream<DynamicTest> findByName() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<DocRef>>() {
                })
                .withTestFunction(testCase -> {
                    final String nameFilter = testCase.getInput();
                    // Need to sort to ensure predictable order for tests
                    return pipelineStore.findByName(nameFilter, true)
                            .stream()
                            .sorted(Comparator.naturalOrder())
                            .collect(Collectors.toList());
                })
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyList())
                .addCase("Pipe", Collections.emptyList())
                .addCase(PIPE_1_NAME.toLowerCase(), Collections.emptyList())
                .addCase(PIPE_1_NAME, List.of(pipe1))
                .addCase("Pipe*", List.of(pipe1, pipe2, pipe3))
                .addCase("*a", List.of(pipe1, pipe2, pipe3))
                .addCase("*2*", List.of(pipe2))
                .addCase("* *aa*", List.of(pipe2, pipe3))
                .build();
    }

    @TestFactory
    Stream<DynamicTest> findByName_no_wild_cards() {
        return TestUtil.buildDynamicTestStream()
                .withInputType(String.class)
                .withWrappedOutputType(new TypeLiteral<List<DocRef>>() {
                })
                .withTestFunction(testCase -> {
                    final String nameFilter = testCase.getInput();
                    // Need to sort to ensure predictable order for tests
                    return pipelineStore.findByName(nameFilter, false)
                            .stream()
                            .sorted(Comparator.naturalOrder())
                            .collect(Collectors.toList());
                })
                .withSimpleEqualityAssertion()
                .addCase(null, Collections.emptyList())
                .addCase("Pipe", Collections.emptyList())
                .addCase(PIPE_1_NAME.toLowerCase(), Collections.emptyList())
                .addCase(PIPE_1_NAME, List.of(pipe1))
                .addCase("Pipe*", Collections.emptyList())
                .addCase("*a", Collections.emptyList())
                .addCase("*2*", Collections.emptyList())
                .addCase("* *aa*", Collections.emptyList())
                .build();
    }
}
