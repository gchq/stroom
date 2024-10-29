package stroom.annotation.shared;

import stroom.test.common.TestUtil;
import stroom.util.shared.UserRef;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class TestCreateEntryRequest {

    protected static final UserRef USER_1 = UserRef
            .builder()
            .uuid("myId1")
            .subjectId("myId1")
            .displayName("myDisplayName1")
            .build();
    protected static final UserRef USER_2 = UserRef
            .builder()
            .uuid("myId2")
            .subjectId("myId2")
            .displayName("myDisplayName2")
            .build();
    protected static final UserRef USER_3 = UserRef
            .builder()
            .uuid("myId3")
            .subjectId("myId3")
            .displayName("myDisplayName3")
            .build();
    protected static final Annotation ANNOTATION = new Annotation(
            1L,
            123,
            Instant.now().toEpochMilli(),
            USER_1.toDisplayString(),
            Instant.now().toEpochMilli(),
            USER_2.toDisplayString(),
            "myTitle",
            "mySubject",
            "myStatus",
            USER_3,
            "comment",
            "history");
    protected static final UserRef USER_4 = UserRef
            .builder()
            .uuid("myId4")
            .subjectId("myId4")
            .subjectId("myDisplayName4")
            .build();

    @Test
    void testSerDeser1() {
        final CreateEntryRequest inputRequest = new CreateEntryRequest(
                ANNOTATION,
                Annotation.ASSIGNED_TO,
                UserRefEntryValue.of(USER_4),
                List.of(
                        EventId.parse("1:10"),
                        EventId.parse("2:11")));

        final CreateEntryRequest outputRequest = TestUtil.testSerialisation(inputRequest, CreateEntryRequest.class);

        Assertions.assertThat(outputRequest.getEntryValue())
                .isNotNull()
                .isInstanceOf(UserRefEntryValue.class)
                .isEqualTo(inputRequest.getEntryValue());
    }

    @Test
    void testSerDeser2() {
        final CreateEntryRequest inputRequest = new CreateEntryRequest(
                ANNOTATION,
                Annotation.ASSIGNED_TO,
                "foo",
                List.of(
                        EventId.parse("1:10"),
                        EventId.parse("2:11")));

        final CreateEntryRequest outputRequest = TestUtil.testSerialisation(inputRequest, CreateEntryRequest.class);

        Assertions.assertThat(outputRequest.getEntryValue())
                .isNotNull()
                .isInstanceOf(StringEntryValue.class)
                .isEqualTo(inputRequest.getEntryValue());
    }
}
