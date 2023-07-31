package stroom.annotation.shared;

import stroom.test.common.TestUtil;
import stroom.util.shared.SimpleUserName;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserNameEntryValue {

    @Test
    void testSerDeser() {
        TestUtil.testSerialisation(
                UserNameEntryValue.of(new SimpleUserName(
                        "myId",
                        "myDisplayName",
                        "myFullName",
                        UUID.randomUUID().toString())),
                UserNameEntryValue.class);
    }

    @Test
    void getAsUiValue() {
        final UserNameEntryValue entryValue = UserNameEntryValue.of(new SimpleUserName(
                "myId",
                "myDisplayName",
                "myFullName",
                UUID.randomUUID().toString()));

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
                uuid));

        assertThat(entryValue.asPersistedValue())
                .isEqualTo(uuid);
    }
}
