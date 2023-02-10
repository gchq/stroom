package stroom.pipeline.xsltfunctions;

import stroom.pipeline.state.CurrentUserHolder;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestCurrentUser extends AbstractXsltFunctionTest<CurrentUser> {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestCurrentUser.class);

    @Mock
    private CurrentUserHolder mockCurrentUserHolder;

    @InjectMocks
    private CurrentUser currentUser;

    @Test
    void call() {

        Mockito.when(mockCurrentUserHolder.getCurrentUser())
                .thenReturn("user1");

        final Sequence sequence1 = callFunctionWithSimpleArgs();

        final Optional<String> optUsername = getAsStringValue(sequence1);

        assertThat(optUsername)
                .hasValue("user1");

        verifyNoLogCalls();
    }

    @Test
    void call_error() {

        Mockito.when(mockCurrentUserHolder.getCurrentUser())
                .thenThrow(new RuntimeException("Bad happened"));

        final Sequence sequence1 = callFunctionWithSimpleArgs();

        assertThat(sequence1)
                .isInstanceOf(EmptyAtomicSequence.class);

        final Optional<String> optUsername = getAsStringValue(sequence1);

        assertThat(optUsername)
                .isEmpty();

        final LogArgs logArgs = verifySingleLogCall();
        assertLogCall(logArgs, Severity.ERROR, "bad happened");
    }

    @Override
    CurrentUser getXsltFunction() {
        return currentUser;
    }

    @Override
    String getFunctionName() {
        return CurrentUser.FUNCTION_NAME;
    }
}
