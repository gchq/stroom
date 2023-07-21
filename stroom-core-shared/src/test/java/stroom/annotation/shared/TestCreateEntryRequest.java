package stroom.annotation.shared;

import stroom.test.common.TestUtil;
import stroom.util.shared.SimpleUserName;
import stroom.util.shared.UserName;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

class TestCreateEntryRequest {

    protected static final UserName USER_1 = SimpleUserName.builder()
            .subjectId("myId1")
            .subjectId("myDisplayName1")
            .build();
    protected static final UserName USER_2 = SimpleUserName.builder()
            .subjectId("myId2")
            .subjectId("myDisplayName2")
            .build();
    protected static final UserName USER_3 = SimpleUserName.builder()
            .subjectId("myId3")
            .subjectId("myDisplayName3")
            .build();
    protected static final Annotation ANNOTATION = new Annotation(
            1L,
            123,
            Instant.now().toEpochMilli(),
            USER_1,
            Instant.now().toEpochMilli(),
            USER_2,
            "myTitle",
            "mySubject",
            "myStatus",
            USER_3,
            "comment",
            "history");
    protected static final UserName USER_4 = SimpleUserName.builder()
            .subjectId("myId4")
            .subjectId("myDisplayName4")
            .build();

    @Test
    void testSerDeser1() {
        final CreateEntryRequest inputRequest = new CreateEntryRequest(
                ANNOTATION,
                Annotation.ASSIGNED_TO,
                UserNameEntryValue.of(USER_4),
                List.of(
                        EventId.parse("1:10"),
                        EventId.parse("2:11")));

        final CreateEntryRequest outputRequest = TestUtil.testSerialisation(inputRequest, CreateEntryRequest.class);

        Assertions.assertThat(outputRequest.getEntryValue())
                .isNotNull()
                .isInstanceOf(UserNameEntryValue.class)
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
