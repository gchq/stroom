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

package stroom.pipeline.xsltfunctions;

import stroom.pipeline.state.CurrentUserHolder;
import stroom.security.api.UserIdentity;
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
                .thenReturn(new UserIdentity() {
                    @Override
                    public String subjectId() {
                        return "user1";
                    }
                });

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
