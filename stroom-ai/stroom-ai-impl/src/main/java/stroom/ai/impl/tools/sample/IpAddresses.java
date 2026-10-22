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

import java.util.List;

/// IP addresses as Python's `ipaddress.ip_address` reads them, without looking any name up: IPv4 as four decimal
/// octets (no leading zeros), IPv6 in any of its written forms (`::`, an IPv4 tail, a `%scope`).
final class IpAddresses {

    private IpAddresses() {
        // Static methods only.
    }

    /// @param text A value.
    /// @return True if it is an IPv4 or IPv6 address.
    static boolean isIpAddress(final String text) {
        return isIpv4(text) || isIpv6(text);
    }

    /// @param text A value.
    /// @return True if it is an IPv4 address: four decimal octets, none with a leading zero.
    static boolean isIpv4(final String text) {
        final List<String> octets = PyText.split(text, ".");
        if (octets.size() != 4) {
            return false;
        }
        for (final String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || !octet.chars().allMatch(c -> c >= '0' && c <= '9')) {
                return false;
            }
            if (octet.length() > 1 && octet.charAt(0) == '0') {
                return false;
            }
            if (Integer.parseInt(octet) > 255) {
                return false;
            }
        }
        return true;
    }

    private static boolean isIpv6(final String text) {
        String address = text;
        final int percent = address.indexOf('%');
        if (percent >= 0) {
            final String scope = address.substring(percent + 1);
            if (scope.isEmpty() || scope.contains("%")) {
                return false;
            }
            address = address.substring(0, percent);
        }
        final List<String> parts = PyText.split(address, ":");
        // An address needs at least 3 parts (::), and at most 9 (8 hextets and a ::).
        if (parts.size() < 3 || parts.size() > 9) {
            return false;
        }
        int hextets = 0;
        int last = parts.size() - 1;
        if (parts.get(last).contains(".")) {
            if (!isIpv4(parts.get(last))) {
                return false;
            }
            hextets += 2;
            last--;
        }
        int skip = -1;
        for (int i = 1; i < last; i++) {
            if (parts.get(i).isEmpty()) {
                if (skip >= 0) {
                    // More than one '::'.
                    return false;
                }
                skip = i;
            }
        }
        int start = 0;
        int end = last;
        if (skip >= 0) {
            if (parts.getFirst().isEmpty() && skip != 1) {
                return false;
            }
            if (parts.get(last).isEmpty() && skip != last - 1) {
                return false;
            }
            if (parts.getFirst().isEmpty()) {
                start = 1;
            }
            if (parts.get(last).isEmpty()) {
                end = last - 1;
            }
        } else {
            if (parts.getFirst().isEmpty() || parts.get(last).isEmpty()) {
                // ':' alone at an end.
                if (!(parts.getFirst().isEmpty() && parts.size() > 1 && parts.get(1).isEmpty())) {
                    return false;
                }
            }
        }
        for (int i = start; i <= end; i++) {
            if (i == skip) {
                continue;
            }
            final String part = parts.get(i);
            if (part.isEmpty() || part.length() > 4 || !part.chars().allMatch(IpAddresses::isHex)) {
                return false;
            }
            hextets++;
        }
        return skip >= 0
                ? hextets < 8
                : hextets == 8;
    }

    private static boolean isHex(final int c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
