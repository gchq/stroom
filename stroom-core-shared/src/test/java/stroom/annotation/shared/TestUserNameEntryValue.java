package stroom.annotation.shared;

import stroom.test.common.TestUtil;
import stroom.util.shared.SimpleUserName;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserNameEntryValue {

    @Test
    void testSerDeser1() {
        TestUtil.testSerialisation(
                UserNameEntryValue.of(new SimpleUserName(
                        "myId",
                        "myDisplayName",
                        "myFullName",
                        UUID.randomUUID().toString(),
                        true)),
                UserNameEntryValue.class);
    }

    @Test
    void testSerDeser2() {
        TestUtil.testSerialisation(
                UserNameEntryValue.of(new SimpleUserName(
                        "myId",
                        "myDisplayName",
                        "myFullName",
                        UUID.randomUUID().toString(),
                        false)),
                UserNameEntryValue.class);
    }

    @Test
    void getAsUiValue() {
        final UserNameEntryValue entryValue = UserNameEntryValue.of(new SimpleUserName(
                "myId",
                "myDisplayName",
                "myFullName",
                UUID.randomUUID().toString(),
                true));

        assertThat(entryValue.asUiValue())
                .isEqualTo("myDisplayName");
    }

    @Test
    void getAsPersistedValue() {
        final String uuid = UUID.randomUUID().toString();
        final UserNameEntryValue entryValue = UserNameEntryValue.of(new SimpleUserName(
                "myId",
                "myDisplayName",
                "myFullName",
                uuid,
                true));

        assertThat(entryValue.asPersistedValue())
                .isEqualTo(uuid);
    }
}
