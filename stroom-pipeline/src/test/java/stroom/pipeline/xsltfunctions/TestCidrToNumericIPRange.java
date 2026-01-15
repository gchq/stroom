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

import stroom.util.net.IpAddressUtil;
import stroom.util.shared.Severity;

import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.UnknownHostException;

import static org.assertj.core.api.Assertions.assertThat;

class TestCidrToNumericIPRange extends AbstractXsltFunctionTest<CidrToNumericIPRange> {

    private CidrToNumericIPRange cidrToNumericIPRange;

    @BeforeEach
    void setUp() {
        cidrToNumericIPRange = new CidrToNumericIPRange();
    }

    @Test
    void call() throws XPathException, UnknownHostException {
        long[] range = cidrToRange("192.168.1.0/24");
        assertThat(range[0])
                .isEqualTo(IpAddressUtil.toNumericIpAddress("192.168.1.0"));
        assertThat(range[1])
                .isEqualTo(IpAddressUtil.toNumericIpAddress("192.168.1.255"));

        range = cidrToRange("10.0.10.32/27");
        assertThat(range[0])
                .isEqualTo(IpAddressUtil.toNumericIpAddress("10.0.10.32"));
        assertThat(range[1])
                .isEqualTo(IpAddressUtil.toNumericIpAddress("10.0.10.63"));

        range = cidrToRange("10.1.0.0/16");
        assertThat(range[0])
                .isEqualTo(IpAddressUtil.toNumericIpAddress("10.1.0.0"));
        assertThat(range[1])
                .isEqualTo(IpAddressUtil.toNumericIpAddress("10.1.255.255"));
    }

    @Test
    void call_invalidCidr() {
        logLogCallsToDebug();

        final Sequence sequence = callFunctionWithSimpleArgs("192.168.1.0");
        assertThat(sequence)
                .isInstanceOf(ArrayItem.class);
        assertThat(((ArrayItem) sequence).isEmpty())
                .isTrue();

        final LogArgs logArgs = verifySingleLogCall();
        Assertions.assertThat(logArgs.getSeverity())
                .isEqualTo(Severity.ERROR);
        Assertions.assertThat(logArgs.getMessage())
                .containsIgnoringCase("invalid cidr format");
    }

    private long[] cidrToRange(final String cidr) throws XPathException {
        final Sequence sequence = callFunctionWithSimpleArgs(cidr);

        assertThat(sequence)
                .isInstanceOf(ArrayItem.class)
                .describedAs("Returned type should be an array");

        final ArrayItem range = ((ArrayItem) sequence);
        assertThat(range.arrayLength())
                .isEqualTo(2)
                .describedAs("Returned array should contain only two items");

        return new long[] {
                Long.parseLong(range.get(0).getStringValue()),
                Long.parseLong(range.get(1).getStringValue())
        };
    }

    @Override
    CidrToNumericIPRange getXsltFunction() {
        return cidrToNumericIPRange;
    }

    @Override
    String getFunctionName() {
        return CidrToNumericIPRange.FUNCTION_NAME;
    }
}
