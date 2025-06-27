package stroom.query.language.functions;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestHostName {

    @Test
    void testOf() {
        final String name = "my-host";
        final HostName hostName = HostName.of(name);
        assertThat(hostName.get()).isEqualTo(name);
    }

    @Test
    void testOf_publicDomain() {
        final String name = "github.com";
        final HostName hostName = HostName.of(name);
        assertThat(hostName.get()).isEqualTo(name);
    }

    @Test
    void testOf_null() {
        assertThatThrownBy(() -> HostName.of(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testEqualsAndHashCode() {
        final HostName hostName1 = HostName.of("host1");
        final HostName hostName2 = HostName.of("host1");
        final HostName hostName3 = HostName.of("host2");

        assertThat(hostName1)
                .isEqualTo(hostName2)
                .hasSameHashCodeAs(hostName2);
        assertThat(hostName1).isNotEqualTo(hostName3);
    }

    @Test
    void testToString() {
        final String name = "my-host.domain.com";
        final HostName hostName = HostName.of(name);
        assertThat(hostName.toString()).isEqualTo(name);
    }
}