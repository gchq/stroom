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

package stroom.util.net;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class IpAddressUtil {

    private IpAddressUtil() {
    }

    public static InetAddress ipAddressFromString(final String address) throws UnknownHostException {
        return InetAddress.getByName(address);
    }

    /**
     * Converts a string-based IPv4 address to its numeric representation
     * @param address An IPv4 address, such as `192.168.1.0`
     * @return A numeric representation of the IP address
     */
    public static long toNumericIpAddress(final String address) throws UnknownHostException {
        final byte[] addressBytes = ipAddressFromString(address).getAddress();
        long value = 0;
        for (final byte b : addressBytes) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }
}
