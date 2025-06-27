package stroom.query.language.functions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestHostAddress {

    @Test
    void testOf_localhost() {
        final HostAddress hostAddress = HostAddress.of("localhost");
        assertThat(hostAddress.getHostAddress()).isEqualTo("127.0.0.1");
        assertThat(hostAddress.getHostName()).isNotNull().isNotBlank();
    }

    @Test
    void testOf_ipAddress() {
        final HostAddress hostAddress = HostAddress.of("127.0.0.1");
        assertThat(hostAddress.getHostAddress()).isEqualTo("127.0.0.1");
        assertThat(hostAddress.getHostName()).isNotNull().isNotBlank();
    }

    @Test
    void testOf_publicDomains() {
        final HostAddress byName = HostAddress.of("github.com");
        assertThat(byName.getHostAddress()).isNotNull().isNotBlank();
        assertThat(byName.getHostName()).isNotNull().isNotBlank();

        final HostAddress byIp = HostAddress.of("8.8.8.8");
        assertThat(byIp.getHostAddress()).isEqualTo("8.8.8.8");
        assertThat(byIp.getHostName()).isNotNull().isNotBlank();
    }

    @Test
    void testOf_unknownHost() {
        assertThatThrownBy(() -> HostAddress.of("a.b.c.d.invalid.host"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown host");
    }

    @Test
    void testEqualsAndHashCode() {
        final HostAddress hostAddress1 = HostAddress.of("localhost");
        final HostAddress hostAddress2 = HostAddress.of("127.0.0.1");
        final HostAddress hostAddress3 = HostAddress.of("8.8.8.8");

        assertThat(hostAddress1)
                .isEqualTo(hostAddress2)
                .hasSameHashCodeAs(hostAddress2);
        assertThat(hostAddress1).isNotEqualTo(hostAddress3);
    }

    @Test
    void testToString() {
        final HostAddress hostAddress = HostAddress.of("localhost");
        assertThat(hostAddress.toString()).contains("127.0.0.1");
    }
}