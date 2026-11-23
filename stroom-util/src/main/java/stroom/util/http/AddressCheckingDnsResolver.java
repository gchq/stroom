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

package stroom.util.http;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Set;

/// Resolves host names for an HTTP client, refusing any host with an address on this machine (including the
/// addresses of its network interfaces), a link-local address or a cloud instance metadata address. Other private
/// network addresses are allowed.
///
/// A client using this resolver checks the address of every connection it makes, including those for
/// redirects, and connects to the addresses that were checked.
public final class AddressCheckingDnsResolver implements DnsResolver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AddressCheckingDnsResolver.class);

    /// The resolver, using the system's own name resolution.
    public static final AddressCheckingDnsResolver INSTANCE =
            new AddressCheckingDnsResolver(SystemDefaultDnsResolver.INSTANCE);

    // Instance metadata addresses that are not link-local.
    private static final Set<InetAddress> REFUSED_ADDRESSES = Set.of(
            literal("100.100.100.200"),
            literal("fd00:ec2::254"));

    private static final byte[] NAT64_PREFIX = {0x00, 0x64, (byte) 0xff, (byte) 0x9b};

    private final DnsResolver delegate;

    AddressCheckingDnsResolver(final DnsResolver delegate) {
        this.delegate = delegate;
    }

    /// Resolves a host name, refusing it if any of its addresses is not allowed.
    ///
    /// @param host The host name or address literal.
    /// @return The addresses of the host.
    /// @throws UnknownHostException If the host can't be resolved or has an address that is not allowed.
    @Override
    public InetAddress[] resolve(final String host) throws UnknownHostException {
        final InetAddress[] addresses = delegate.resolve(host);
        for (final InetAddress address : addresses) {
            if (!isAllowed(address)) {
                LOGGER.debug("Refused host '{}' with address {}", host, address);
                throw new UnknownHostException(LogUtil.message("Host '{}' is not allowed", host));
            }
        }
        return addresses;
    }

    /// Resolves the canonical name of a host, without checking its addresses.
    ///
    /// @param host The host name.
    /// @return The canonical host name.
    /// @throws UnknownHostException If the host can't be resolved.
    @Override
    public String resolveCanonicalHostname(final String host) throws UnknownHostException {
        return delegate.resolveCanonicalHostname(host);
    }

    /// Tests whether an address may be connected to.
    ///
    /// @param address The address.
    /// @return False for any address on this machine, including those of its network interfaces, a link-local
    /// address or an instance metadata address, including an IPv6 address holding one of these IPv4 addresses.
    public static boolean isAllowed(final InetAddress address) {
        final InetAddress ipv4 = getEmbeddedIpv4(address);
        if (ipv4 != null) {
            return isAllowed(ipv4);
        }
        final boolean thisNetwork = address instanceof Inet4Address
                                    && address.getAddress()[0] == 0;
        return !(address.isAnyLocalAddress()
                 || address.isLoopbackAddress()
                 || address.isLinkLocalAddress()
                 || thisNetwork
                 || REFUSED_ADDRESSES.contains(address)
                 || isInterfaceAddress(address));
    }

    // Tests whether an address belongs to one of this machine's network interfaces, whether or not it is up.
    private static boolean isInterfaceAddress(final InetAddress address) {
        try {
            return NetworkInterface.getByInetAddress(address) != null;
        } catch (final SocketException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to check interfaces for {}: {}", address, e.getMessage()));
            // Refused if it can't be checked.
            return true;
        }
    }

    // Returns the IPv4 address held in an IPv4-compatible, IPv4-mapped or NAT64 IPv6 address, or null if it is
    // none of these.
    private static InetAddress getEmbeddedIpv4(final InetAddress address) {
        if (!(address instanceof Inet6Address)) {
            return null;
        }
        final byte[] bytes = address.getAddress();
        final boolean compatible = isZero(bytes, 0, 12);
        final boolean mapped = isZero(bytes, 0, 10)
                               && bytes[10] == (byte) 0xff
                               && bytes[11] == (byte) 0xff;
        final boolean nat64 = Arrays.equals(bytes, 0, 4, NAT64_PREFIX, 0, 4)
                              && isZero(bytes, 4, 12);
        if (!compatible && !mapped && !nat64) {
            return null;
        }
        try {
            return InetAddress.getByAddress(Arrays.copyOfRange(bytes, 12, 16));
        } catch (final UnknownHostException e) {
            // Not possible for four bytes.
            throw new IllegalStateException(e);
        }
    }

    private static boolean isZero(final byte[] bytes, final int from, final int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    // Parses an address literal, which never needs a name lookup.
    private static InetAddress literal(final String address) {
        try {
            return InetAddress.getByName(address);
        } catch (final UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
