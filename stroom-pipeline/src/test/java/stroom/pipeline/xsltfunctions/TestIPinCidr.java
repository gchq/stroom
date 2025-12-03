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

import stroom.util.shared.Severity;

import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestIPinCidr extends AbstractXsltFunctionTest<IPInCidr> {

    private IPInCidr ipInCidr;

    @BeforeEach
    void setUp() {
        ipInCidr = new IPInCidr();
    }

    @Test
    void call() {
        assertThat(ipInCidr("192.168.1.10", "192.168.1.0/24"))
                .isEqualTo(true)
                .describedAs("IP within CIDR block should return true");
        assertThat(ipInCidr("192.168.2.10", "192.168.1.0/24"))
                .isEqualTo(false)
                .describedAs("IP outside CIDR block should return false");
        assertThat(ipInCidr("10.20.0.250", "10.20.0.0/16"))
                .isEqualTo(true)
                .describedAs("IP within CIDR block should return true");
        assertThat(ipInCidr("10.21.0.250", "10.20.0.0/16"))
                .isEqualTo(false)
                .describedAs("IP outside CIDR block should return false");
    }

    @Test
    void call_invalidIPAddress() {
        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs("192.168.3.300", "192.168.1.0/24");
        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();
        Assertions.assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.ERROR);
        Assertions.assertThat(logArgs.getMessage())
                .containsIgnoringCase("invalid ip address");
    }

    @Test
    void call_invalidCidr() {
        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs("192.168.1.1", "192.168.1.0");
        assertThat(sequence)
                .isInstanceOf(EmptyAtomicSequence.class);

        final LogArgs logArgs = verifySingleLogCall();
        Assertions.assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.ERROR);
        Assertions.assertThat(logArgs.getMessage())
                .containsIgnoringCase("invalid cidr format");
    }

    private boolean ipInCidr(final String ipAddress, final String cidr) {
        final Sequence sequence = callFunctionWithSimpleArgs(ipAddress, cidr);

        return getAsBooleanValue(sequence)
                .orElseThrow();
    }

    @Override
    IPInCidr getXsltFunction() {
        return ipInCidr;
    }

    @Override
    String getFunctionName() {
        return IPInCidr.FUNCTION_NAME;
    }
}
