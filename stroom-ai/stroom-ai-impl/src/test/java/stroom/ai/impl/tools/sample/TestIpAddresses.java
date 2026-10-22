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

package stroom.ai.impl.tools.sample;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestIpAddresses {

    @Test
    void ipAddressesAreReadAsPythonReadsThem() {
        assertThat(IpAddresses.isIpAddress("10.0.0.1")).isTrue();
        // Python refuses an octet with a leading zero.
        assertThat(IpAddresses.isIpAddress("010.0.0.1")).isFalse();
        assertThat(IpAddresses.isIpAddress("256.0.0.1")).isFalse();
        assertThat(IpAddresses.isIpAddress("::1")).isTrue();
        assertThat(IpAddresses.isIpAddress("fe80::1%eth0")).isTrue();
        assertThat(IpAddresses.isIpAddress("2001:db8::8a2e:370:7334")).isTrue();
        assertThat(IpAddresses.isIpAddress("::ffff:192.0.2.1")).isTrue();
        assertThat(IpAddresses.isIpAddress("1:2:3:4:5:6:7:8")).isTrue();
        assertThat(IpAddresses.isIpAddress("1:2:3:4:5:6:7")).isFalse();
        assertThat(IpAddresses.isIpAddress("1::2::3")).isFalse();
        // A time is not an address.
        assertThat(IpAddresses.isIpAddress("10:00:00")).isFalse();
        assertThat(IpAddresses.isIpAddress("alice")).isFalse();
    }
}
