package stroom.event.logging.impl;

import stroom.activity.api.CurrentActivity;
import stroom.event.logging.api.ObjectInfoProvider;
import stroom.event.logging.api.ObjectType;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;
import stroom.util.shared.HasName;

import event.logging.AuthenticateEventAction;
import event.logging.BaseObject;
import event.logging.Data;
import event.logging.OtherObject;
import event.logging.Outcome;
import event.logging.Resource;
import event.logging.User;
import event.logging.ViewEventAction;
import event.logging.impl.LogReceiver;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestStroomEventLoggingServiceImpl {

    private static final String TYPE_ID = "typeId";
    private static final String DESCRIPTION = "I did something";
    private SecurityContext securityContext = new MockSecurityContext();
    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private CurrentActivity currentActivity;
    @Mock
    private BuildInfo buildInfo;

    private StroomEventLoggingService stroomEventLoggingService;

    private BaseObject testObj = OtherObject.builder().withDescription("Test").build();

    @BeforeEach
    void setup() {
        final Map<ObjectType, Provider<ObjectInfoProvider>> objectInfoProviderMap =
                new HashMap<>();
        objectInfoProviderMap.put(new ObjectType(TestObj.class), () -> new TestObjInfoProvider());

        stroomEventLoggingService = new StroomEventLoggingServiceImpl(
                new LoggingConfig(),
                securityContext,
                () -> httpServletRequest,
                objectInfoProviderMap,
                currentActivity,
                () -> buildInfo);

        LocalLogReceiver.getEvents()
                .clear();

        // Make the event logger use our log receiver so we can get the events
        System.setProperty("event.logging.logreceiver", LocalLogReceiver.class.getName());
    }

    @Test
    void testLoggedAction_failure() {

        final String exceptionMsg = "Bad stuff happened";
        Assertions
                .assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> {
                    stroomEventLoggingService.loggedAction(
                            TYPE_ID,
                            DESCRIPTION,
                            ViewEventAction.builder()
                                    .addResource(Resource.builder()
                                            .withURL("localhost")
                                            .build())
                                    .build(),
                            () -> {
                                // Do some work
                                throw new RuntimeException(exceptionMsg);
                            });
                })
                .withMessage(exceptionMsg);

        assertThat(LocalLogReceiver.getEvents())
                .hasSize(1);
        final String xml = LocalLogReceiver.getEvents().get(0);
        assertTagValue(xml, "Description", exceptionMsg);
        assertTagValue(xml, "Success", "false");
    }

    @Test
    void testLoggedAction_failure_overwriteOutcome() {

        final String exceptionMsg = "Bad stuff happened";
        Assertions
                .assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> {
                    stroomEventLoggingService.loggedAction(
                            TYPE_ID,
                            DESCRIPTION,
                            ViewEventAction.builder()
                                    .addResource(Resource.builder()
                                            .withURL("localhost")
                                            .build())
                                    .withOutcome(Outcome.builder()
                                            .withSuccess(true) // will be overwritten
                                            .withDescription("It worked") // will be overwritten
                                            .build())
                                    .build(),
                            () -> {
                                // Do some work
                                throw new RuntimeException(exceptionMsg);
                            });
                })
                .withMessage(exceptionMsg);

        assertThat(LocalLogReceiver.getEvents())
                .hasSize(1);
        final String xml = LocalLogReceiver.getEvents().get(0);
        assertTagValue(xml, "Description", exceptionMsg);
        assertTagValue(xml, "Success", "false");
    }

    @Test
    void testLoggedAction_success() {

        AtomicBoolean wasWorkDone = new AtomicBoolean(false);

        stroomEventLoggingService.loggedAction(
                "typeId",
                "desc",
                ViewEventAction.builder()
                        .addResource(Resource.builder()
                                .withURL("localhost")
                                .build())
                        .build(),
                () -> {
                    // Do some work
                    wasWorkDone.set(true);
                });

        assertThat(wasWorkDone)
                .isTrue();

        assertThat(LocalLogReceiver.getEvents())
                .hasSize(1);

        final String xml = LocalLogReceiver.getEvents().get(0);
        assertThat(xml)
                .doesNotContainPattern("<Success>.*</Success>");
    }

    @Test
    void testLoggedResult_failure() {

        final String exceptionMsg = "Bad stuff happened";
        Assertions
                .assertThatExceptionOfType(RuntimeException.class)
                .isThrownBy(() -> {
                    stroomEventLoggingService.loggedResult(
                            "typeId",
                            "desc",
                            AuthenticateEventAction.builder()
                                    .withUser(User.builder()
                                            .withName("jbloggs")
                                            .build())
                                    .build(),
                            () -> {
                                // Do some work
                                throw new RuntimeException(exceptionMsg);
                            });
                })
                .withMessage(exceptionMsg);

        assertThat(LocalLogReceiver.getEvents())
                .hasSize(1);
        final String xml = LocalLogReceiver.getEvents().get(0);
        assertTagValue(xml, "Description", exceptionMsg);
        assertTagValue(xml, "Success", "false");
    }

    @Test
    void testLoggedResult_success() {

        final Boolean result = stroomEventLoggingService.loggedResult(
                "typeId",
                "desc",
                AuthenticateEventAction.builder()
                        .withUser(User.builder()
                                .withName("jbloggs")
                                .build())
                        .build(),
                () -> {
                    // Do some work
                    return true;
                });

        assertThat(result)
                .isTrue();

        assertThat(LocalLogReceiver.getEvents())
                .hasSize(1);

        final String xml = LocalLogReceiver.getEvents().get(0);
        assertThat(xml)
                .doesNotContainPattern("<Success>.*</Success>");
    }

    @Test
    void testDataItemCreationAndRedaction () throws Exception {
        List<Data> allData = stroomEventLoggingService.getDataItems
                (new TestSecretObj("test", "xyzzy", "open-sesame"));

        assertThat(allData.size()).isEqualTo(4);
        assertThat(allData).anyMatch(data -> data.getName().equals("name"));
        assertThat(allData).anyMatch(data -> data.getName().equals("password"));
        assertThat(allData).anyMatch(data -> data.getName().equals("myNewSecret"));
        assertThat(allData).anyMatch(data -> data.getName().equals("secret"));

        assertThat(allData).noneMatch(data -> data.getValue().equals("xyzzy"));
        assertThat(allData).noneMatch(data -> data.getValue().equals("open-sesame"));
        assertThat(allData.stream().filter(data -> data.getValue().equals("test"))
                .collect(Collectors.toList()).size()).isEqualTo(1);
        assertThat(allData.stream().filter(data -> data.getValue().equals("false"))
                .collect(Collectors.toList()).size()).isEqualTo(1);
    }

    @Test
    void testConvertPojoWithInfoPrvider () throws Exception {
        BaseObject baseObject = stroomEventLoggingService.convert(new TestObj());
        assertThat(baseObject).isSameAs(testObj);
    }

    @Test
    void testConvertPojoWithoutInfoPrvider () throws Exception {
        String name = "TestSecretObject1";
        String typeName = TestSecretObj.class.getSimpleName();
        BaseObject baseObject = stroomEventLoggingService.convert(new TestSecretObj(name, "b", "x"));

        assertThat(baseObject.getType()).isEqualTo(typeName);

        String description = baseObject.getDescription();
        assertThat(baseObject.getName()).isEqualTo(name);

        assertThat(description).contains(name);
        assertThat(description).contains(typeName);
    }

    private void assertTagValue(final String xml, final String tag, final String value) {
        assertThat(xml)
                .contains(LogUtil.message("<{}>{}</{}>", tag, value, tag));
    }

    public static class LocalLogReceiver implements LogReceiver {

        private static final Logger LOGGER = LoggerFactory.getLogger(LocalLogReceiver.class);

        private static final List<String> EVENTS = new ArrayList<>();

        @Override
        public void log(final String data) {

            LOGGER.info("Received event\n{}", data);
            EVENTS.add(data);
        }

        public LocalLogReceiver() {
        }

        public static List<String> getEvents() {
            return EVENTS;
        }
    }

    public static class TestObj {

    }

    public class TestObjInfoProvider implements ObjectInfoProvider {

        @Override
        public BaseObject createBaseObject(final Object object) {
            return testObj;
        }

        @Override
        public String getObjectType(final Object object) {
            return "Test Object";
        }
    }

    public static class TestSecretObj implements HasName {
        private String name;
        private String password;
        private String myNewSecret;
        private boolean secret;

        public TestSecretObj (String name, String password, String myNewSecret){
            this.name = name;
            this.password = password;
            this.myNewSecret = myNewSecret;
        }

        public String getMyNewSecret() {
            return myNewSecret;
        }

        public void setMyNewSecret(final String myNewSecret) {
            this.myNewSecret = myNewSecret;
        }

        public String getPassword() {
            return password;
        }

        public String getName() {
            return name;
        }

        public Boolean isSecret() {
            return secret;
        }

        @Override
        public void setName(final String name) {

        }
    }
}