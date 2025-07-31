package stroom.annotation.shared;

import stroom.test.common.TestUtil;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserNameEntryValue {

    @Test
    void testSerDeser() {
        TestUtil.testSerialisation(
                UserRefEntryValue.of(UserRef
                        .builder()
                        .uuid(UUID.randomUUID().toString())
                        .subjectId("myId")
                        .displayName("myDisplayName")
                        .fullName("myFullName")
                        .build()),
                UserRefEntryValue.class);
    }

    @Test
    void getAsUiValue() {
        final UserRefEntryValue entryValue = UserRefEntryValue.of(UserRef
                .builder()
                .uuid(UUID.randomUUID().toString())
                .subjectId("myId")
                .displayName("myDisplayName")
                .fullName("myFullName")
                .build());
        assertThat(entryValue.asUiValue())
                .isEqualTo("myDisplayName");
    }
}
