package stroom.event.logging.impl;

import stroom.activity.api.CurrentActivity;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;

import event.logging.AuthenticateEventAction;
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

import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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


    @BeforeEach
    void setup() {
        stroomEventLoggingService = new StroomEventLoggingServiceImpl(
                securityContext,
                () -> httpServletRequest,
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

        System.out.println(LocalLogReceiver.getEvents().get(0));
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

        System.out.println(LocalLogReceiver.getEvents().get(0));
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

        System.out.println(LocalLogReceiver.getEvents().get(0));
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

        System.out.println(LocalLogReceiver.getEvents().get(0));
    }

    private void assertTagValue(final String xml, final String tag, final String value) {
        assertThat(xml)
                .contains(LogUtil.message("<{}>{}</{}>", tag, value, tag));
    }

    public static class LocalLogReceiver implements LogReceiver {

        private static final List<String> EVENTS = new ArrayList<>();

        @Override
        public void log(final String data) {
            EVENTS.add(data);
        }

        public LocalLogReceiver() {
        }

        public static List<String> getEvents() {
            return EVENTS;
        }
    }
}