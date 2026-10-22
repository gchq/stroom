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

package stroom.ai.impl.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// IP addresses as Python 3.12's `ipaddress.ip_address` reads them, for the sample profiler (a field of
/// addresses) and the index template check (a value for an `ip` field).
class TestIpAddresses {

    @Test
    void ipAddressesAreReadAsPythonReadsThem() {
        assertThat(IpAddresses.isAddress("10.0.0.1")).isTrue();
        // Python refuses an octet with a leading zero.
        assertThat(IpAddresses.isAddress("010.0.0.1")).isFalse();
        assertThat(IpAddresses.isAddress("256.0.0.1")).isFalse();
        assertThat(IpAddresses.isAddress("::1")).isTrue();
        assertThat(IpAddresses.isAddress("fe80::1%eth0")).isTrue();
        assertThat(IpAddresses.isAddress("2001:db8::8a2e:370:7334")).isTrue();
        assertThat(IpAddresses.isAddress("::ffff:192.0.2.1")).isTrue();
        assertThat(IpAddresses.isAddress("1:2:3:4:5:6:7:8")).isTrue();
        assertThat(IpAddresses.isAddress("1:2:3:4:5:6:7")).isFalse();
        assertThat(IpAddresses.isAddress("1::2::3")).isFalse();
        // A time is not an address.
        assertThat(IpAddresses.isAddress("10:00:00")).isFalse();
        assertThat(IpAddresses.isAddress("alice")).isFalse();
    }

    @Test
    void anIpv4TailAfterADoubleColonIsAnAddress() {
        // The sample profiler's own reader refused these, which Python takes.
        assertThat(IpAddresses.isAddress("64:ff9b::192.0.2.1")).isTrue();
        assertThat(IpAddresses.isAddress("01::1.2.3.4")).isTrue();
        assertThat(IpAddresses.isAddress("1:2:3:4:5:6:1.2.3.4")).isTrue();
        assertThat(IpAddresses.isAddress("1:2:3:4:5:6:7:1.2.3.4")).isFalse();
        assertThat(IpAddresses.isAddress("00:::1.2.3.40")).isFalse();
    }

    @Test
    void anIndexTemplatesIpValuesAreCheckedAlike() {
        assertThat(IpAddresses.isAddress("")).isFalse();
        assertThat(IpAddresses.isAddress("::")).isTrue();
        assertThat(IpAddresses.isAddress("1::")).isTrue();
        assertThat(IpAddresses.isAddress(":1:2:3:4:5:6:7")).isFalse();
        assertThat(IpAddresses.isAddress("1:2:3:4:5:6:7:8:9")).isFalse();
        assertThat(IpAddresses.isAddress("fe80::1%")).isFalse();
        assertThat(IpAddresses.isAddress("1.2.3.4%x")).isFalse();
        assertThat(IpAddresses.isAddress("1:2:3:4:5:6:7:8%1")).isTrue();
        assertThat(IpAddresses.isIpv4("1.2.3")).isFalse();
        assertThat(IpAddresses.isIpv6("::ffff:1.2.3.4")).isTrue();
    }
}
