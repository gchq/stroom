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

import java.util.ArrayList;
import java.util.List;

/// IP addresses as the tools check a document's values: an IPv4 address of four decimal octets with no leading
/// zeros, or an IPv6 address (at most one `::`, an IPv4 address as its last part, an optional `%scope`).
/// Elasticsearch's ip type takes what it takes.
public final class IpAddresses {

    private static final int HEXTETS = 8;

    private IpAddresses() {
        // Static only.
    }

    /// @param text A text.
    /// @return True if it is an IPv4 or IPv6 address.
    public static boolean isAddress(final String text) {
        return isIpv4(text) || isIpv6(text);
    }

    /// @param text A text.
    /// @return True if it is an IPv4 address.
    public static boolean isIpv4(final String text) {
        return ipv4(text) >= 0;
    }

    // The address as a number, or -1 if it isn't one.
    private static long ipv4(final String text) {
        if (text.isEmpty()) {
            return -1;
        }
        final String[] octets = text.split("\\.", -1);
        if (octets.length != 4) {
            return -1;
        }
        long value = 0;
        for (final String octet : octets) {
            if (octet.isEmpty() || octet.length() > 3 || !octet.chars().allMatch(c -> c >= '0' && c <= '9')
                || (!"0".equals(octet) && octet.charAt(0) == '0')) {
                return -1;
            }
            final int number = Integer.parseInt(octet);
            if (number > 255) {
                return -1;
            }
            value = (value << 8) | number;
        }
        return value;
    }

    /// @param text A text.
    /// @return True if it is an IPv6 address (with an optional scope).
    public static boolean isIpv6(final String text) {
        final int percent = text.indexOf('%');
        final String address;
        if (percent >= 0) {
            final String scope = text.substring(percent + 1);
            if (scope.isEmpty() || scope.contains("%")) {
                return false;
            }
            address = text.substring(0, percent);
        } else {
            address = text;
        }
        if (address.isEmpty()) {
            return false;
        }
        final List<String> parts = new ArrayList<>(List.of(address.split(":", -1)));
        if (parts.size() < 3) {
            return false;
        }
        if (parts.getLast().contains(".")) {
            final long ipv4 = ipv4(parts.removeLast());
            if (ipv4 < 0) {
                return false;
            }
            parts.add(Long.toHexString((ipv4 >> 16) & 0xFFFF));
            parts.add(Long.toHexString(ipv4 & 0xFFFF));
        }
        if (parts.size() > HEXTETS + 1) {
            return false;
        }
        int skip = -1;
        for (int i = 1; i < parts.size() - 1; i++) {
            if (parts.get(i).isEmpty()) {
                if (skip >= 0) {
                    return false;
                }
                skip = i;
            }
        }
        final int high;
        final int low;
        if (skip >= 0) {
            int partsHigh = skip;
            int partsLow = parts.size() - skip - 1;
            if (parts.getFirst().isEmpty()) {
                partsHigh--;
                if (partsHigh != 0) {
                    return false;
                }
            }
            if (parts.getLast().isEmpty()) {
                partsLow--;
                if (partsLow != 0) {
                    return false;
                }
            }
            if (HEXTETS - (partsHigh + partsLow) < 1) {
                return false;
            }
            high = partsHigh;
            low = partsLow;
        } else {
            if (parts.size() != HEXTETS || parts.getFirst().isEmpty() || parts.getLast().isEmpty()) {
                return false;
            }
            high = parts.size();
            low = 0;
        }
        for (int i = 0; i < high; i++) {
            if (!hextet(parts.get(i))) {
                return false;
            }
        }
        for (int i = parts.size() - low; i < parts.size(); i++) {
            if (!hextet(parts.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean hextet(final String text) {
        return !text.isEmpty() && text.length() <= 4 && text.chars().allMatch(c -> (c >= '0' && c <= '9')
                                                                                   || (c >= 'a' && c <= 'f')
                                                                                   || (c >= 'A' && c <= 'F'));
    }
}
