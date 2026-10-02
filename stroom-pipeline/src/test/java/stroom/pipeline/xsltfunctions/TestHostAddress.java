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

package stroom.pipeline.xsltfunctions;

import stroom.util.shared.Severity;

import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class TestHostAddress extends AbstractXsltFunctionTest<HostAddress> {

    // Reserved by RFC 6761 so should never resolve
    private static final String UNRESOLVABLE_HOST = "no-such-host.invalid";

    private HostAddress hostAddress;

    @BeforeEach
    void setUp() {
        hostAddress = new HostAddress();
    }

    @Test
    void call() {
        final Sequence sequence = callFunctionWithSimpleArgs("127.0.0.1");

        assertThat(getAsStringValue(sequence))
                .hasValue("127.0.0.1");
        verifyNoLogCalls();
    }

    @Test
    void call_unknownHost() {
        assumeHostIsUnresolvable();
        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs(UNRESOLVABLE_HOST);

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();
        assertLogCall(logArgs, Severity.WARNING, UNRESOLVABLE_HOST);
    }

    @Test
    void call_unknownHost_ignoreWarningsFalse() {
        assumeHostIsUnresolvable();
        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs(UNRESOLVABLE_HOST, false);

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();
        assertLogCall(logArgs, Severity.WARNING, UNRESOLVABLE_HOST);
    }

    @Test
    void call_unknownHost_ignoreWarningsTrue() {
        assumeHostIsUnresolvable();

        final Sequence sequence = callFunctionWithSimpleArgs(UNRESOLVABLE_HOST, true);

        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        // The lookup failed but we asked for the warning to be suppressed
        verifyNoLogCalls();
    }

    /**
     * Some DNS resolvers will resolve anything, in which case there is nothing to test.
     */
    private static void assumeHostIsUnresolvable() {
        boolean isUnresolvable;
        try {
            InetAddress.getByName(UNRESOLVABLE_HOST);
            isUnresolvable = false;
        } catch (final UnknownHostException e) {
            isUnresolvable = true;
        }
        Assumptions.assumeTrue(
                isUnresolvable,
                () -> "Host '" + UNRESOLVABLE_HOST + "' unexpectedly resolves on this machine");
    }

    @Override
    HostAddress getXsltFunction() {
        return hostAddress;
    }

    @Override
    String getFunctionName() {
        return HostAddress.FUNCTION_NAME;
    }
}
