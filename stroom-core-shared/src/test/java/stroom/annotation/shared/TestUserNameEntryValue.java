package stroom.annotation.shared;

import stroom.test.common.TestUtil;
import stroom.util.shared.SimpleUserName;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserNameEntryValue {

    @Test
    void testSerDeser() {
        TestUtil.testSerialisation(
                UserNameEntryValue.of(new SimpleUserName(
                        "myId",
                        "myDisplayName",
                        "myFullName")),
                UserNameEntryValue.class);
    }

    @Test
    void getAsUiValue() {
        final UserNameEntryValue entryValue = UserNameEntryValue.of(new SimpleUserName(
                "myId",
                "myDisplayName",
                "myFullName"));

        assertThat(entryValue.asUiValue())
                .isEqualTo("myDisplayName");
    }

    @Test
    void getAsPersistedValue() {
        final UserNameEntryValue entryValue = UserNameEntryValue.of(new SimpleUserName(
                "myId",
                "myDisplayName",
                "myFullName"));

        assertThat(entryValue.asPersistedValue())
                .isEqualTo("myId");
    }
}
