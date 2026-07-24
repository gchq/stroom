/*
 * Copyright 2016-2026 Crown Copyright
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

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Guards outbound HTTP requests against server-side request forgery (SSRF) by rejecting a URL whose host
 * resolves to an address that should not be reachable from the given call site.
 * <p>
 * Two levels are offered because the safe set differs by call site:
 * <ul>
 *     <li>{@link #requirePublicHost(String)} for calls that must reach a <em>public</em> host (e.g. a public
 *     API) — it rejects loopback, link-local, private/RFC1918, IPv6 unique-local, carrier-grade NAT, wildcard
 *     and multicast.</li>
 *     <li>{@link #rejectMetadataAndWildcard(String)} for calls that may legitimately target an internal host
 *     (e.g. an internal Elasticsearch cluster on a private/loopback address) — it rejects only the
 *     never-legitimate targets: link-local (which includes the cloud-metadata address 169.254.169.254),
 *     wildcard and multicast.</li>
 * </ul>
 * The host is resolved and every returned address is checked, so a hostname that maps to a blocked address is
 * also rejected. Resolution failure is treated as a failure (fail closed). Note: this checks the address at
 * call time, so it does not by itself defeat DNS-rebinding or HTTP redirects to a different host — callers
 * that follow redirects should disable redirect-following or re-check each hop.
 */
public final class SsrfGuard {

    private SsrfGuard() {
    }

    /**
     * @throws IllegalArgumentException if the URL is malformed, its host cannot be resolved, or any resolved
     *                                  address is not a public address.
     */
    public static void requirePublicHost(final String url) {
        check(url, true);
    }

    /**
     * @throws IllegalArgumentException if the URL is malformed, its host cannot be resolved, or any resolved
     *                                  address is link-local (cloud metadata), wildcard or multicast.
     */
    public static void rejectMetadataAndWildcard(final String url) {
        check(url, false);
    }

    private static void check(final String url, final boolean publicOnly) {
        final String host = extractHost(url);
        final InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (final UnknownHostException e) {
            throw new IllegalArgumentException("Could not resolve host '" + host + "' for URL: " + url);
        }
        for (final InetAddress address : addresses) {
            if (isBlocked(address, publicOnly)) {
                throw new IllegalArgumentException(
                        "Refusing to connect to disallowed address " + address.getHostAddress()
                        + " for URL: " + url);
            }
        }
    }

    private static String extractHost(final String url) {
        final URI uri;
        try {
            uri = new URI(url);
        } catch (final URISyntaxException e) {
            throw new IllegalArgumentException("Malformed URL: " + url);
        }
        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException("Could not determine host from URL: " + url);
        }
        // Strip the brackets from an IPv6 literal so InetAddress can parse it.
        if (host.length() > 1 && host.charAt(0) == '[' && host.charAt(host.length() - 1) == ']') {
            host = host.substring(1, host.length() - 1);
        }
        return host;
    }

    private static boolean isBlocked(final InetAddress address, final boolean publicOnly) {
        // Never a legitimate remote target, whatever the call site.
        if (address.isLinkLocalAddress()      // 169.254.0.0/16 (incl. cloud metadata) + fe80::/10
            || address.isAnyLocalAddress()     // 0.0.0.0 / ::
            || address.isMulticastAddress()) {
            return true;
        }
        if (publicOnly) {
            return address.isLoopbackAddress()  // 127.0.0.0/8 + ::1
                   || address.isSiteLocalAddress()  // 10/8, 172.16/12, 192.168/16 (IPv4 RFC1918)
                   || isUniqueLocalIpv6(address)    // fc00::/7 (IPv6 ULA)
                   || isCarrierGradeNat(address);   // 100.64.0.0/10
        }
        return false;
    }

    private static boolean isUniqueLocalIpv6(final InetAddress address) {
        return address instanceof Inet6Address
               && (address.getAddress()[0] & 0xfe) == 0xfc;
    }

    private static boolean isCarrierGradeNat(final InetAddress address) {
        final byte[] bytes = address.getAddress();
        // 100.64.0.0/10
        return bytes.length == 4
               && (bytes[0] & 0xff) == 100
               && (bytes[1] & 0xc0) == 0x40;
    }
}
