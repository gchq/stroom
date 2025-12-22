/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.event.logging.impl;

import stroom.activity.api.CurrentActivity;
import stroom.docref.HasName;
import stroom.event.logging.api.ObjectInfoProvider;
import stroom.event.logging.api.ObjectType;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.util.logging.LogUtil;
import stroom.util.shared.BuildInfo;

import event.logging.AuthenticateEventAction;
import event.logging.BaseObject;
import event.logging.Data;
import event.logging.Device;
import event.logging.OtherObject;
import event.logging.Outcome;
import event.logging.Resource;
import event.logging.User;
import event.logging.ViewEventAction;
import event.logging.impl.LogReceiver;
import jakarta.inject.Provider;
import jakarta.servlet.http.HttpServletRequest;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestStroomEventLoggingServiceImpl {

    private static final String TYPE_ID = "typeId";
    private static final String DESCRIPTION = "I did something";
    private final SecurityContext securityContext = new MockSecurityContext();
    @Mock
    private HttpServletRequest httpServletRequest;
    @Mock
    private CurrentActivity currentActivity;
    @Mock
    private BuildInfo buildInfo;

    private StroomEventLoggingService stroomEventLoggingService;

    private final BaseObject testObj = OtherObject.builder().withDescription("Test").build();

    @BeforeEach
    void setup() {
        final Map<ObjectType, Provider<ObjectInfoProvider>> objectInfoProviderMap =
                new HashMap<>();
        objectInfoProviderMap.put(new ObjectType(TestObj.class), TestObjInfoProvider::new);

        stroomEventLoggingService = new StroomEventLoggingServiceImpl(
                LoggingConfig::new,
                securityContext,
                () -> httpServletRequest,
                objectInfoProviderMap,
                currentActivity,
                () -> buildInfo,
                (ipAddress -> {
                    final Device device = new Device();
                    device.setIPAddress(ipAddress);
                    return device;
                }));

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
                    stroomEventLoggingService.loggedWorkBuilder()
                            .withTypeId(TYPE_ID)
                            .withDescription(DESCRIPTION)
                            .withDefaultEventAction(ViewEventAction.builder()
                                    .addResource(Resource.builder()
                                            .withURL("localhost")
                                            .build())
                                    .build())
                            .withSimpleLoggedAction(() -> {
                                // Do some work
                                throw new RuntimeException(exceptionMsg);
                            })
                            .runActionAndLog();
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
                    stroomEventLoggingService.loggedWorkBuilder()
                            .withTypeId(TYPE_ID)
                            .withDescription(DESCRIPTION)
                            .withDefaultEventAction(ViewEventAction.builder()
                                    .addResource(Resource.builder()
                                            .withURL("localhost")
                                            .build())
                                    .withOutcome(Outcome.builder()
                                            .withSuccess(true) // will be overwritten
                                            .withDescription("It worked") // will be overwritten
                                            .build())
                                    .build())
                            .withSimpleLoggedAction(() -> {
                                // Do some work
                                throw new RuntimeException(exceptionMsg);
                            })
                            .runActionAndLog();
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

        final AtomicBoolean wasWorkDone = new AtomicBoolean(false);

        stroomEventLoggingService.loggedWorkBuilder()
                .withTypeId("typeId")
                .withDescription("desc")
                .withDefaultEventAction(ViewEventAction.builder()
                        .addResource(Resource.builder()
                                .withURL("localhost")
                                .build())
                        .build())
                .withSimpleLoggedAction(() -> {
                    // Do some work
                    wasWorkDone.set(true);
                })
                .runActionAndLog();

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
                    stroomEventLoggingService.loggedWorkBuilder()
                            .withTypeId("typeId")
                            .withDescription("desc")
                            .withDefaultEventAction(AuthenticateEventAction.builder()
                                    .withUser(User.builder()
                                            .withName("jbloggs")
                                            .build())
                                    .build())
                            .withSimpleLoggedResult(() -> {
                                // Do some work
                                throw new RuntimeException(exceptionMsg);
                            })
                            .getResultAndLog();
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

        final Boolean result = stroomEventLoggingService.loggedWorkBuilder()
                .withTypeId("typeId")
                .withDescription("desc")
                .withDefaultEventAction(AuthenticateEventAction.builder()
                        .withUser(User.builder()
                                .withName("jbloggs")
                                .build())
                        .build())
                .withSimpleLoggedResult(() -> {
                    // Do some work
                    return true;
                })
                .getResultAndLog();

        assertThat(result)
                .isTrue();

        assertThat(LocalLogReceiver.getEvents())
                .hasSize(1);

        final String xml = LocalLogReceiver.getEvents().get(0);
        assertThat(xml)
                .doesNotContainPattern("<Success>.*</Success>");
    }

    @Test
    void testDataItemCreationAndRedaction() throws Exception {
        final List<Data> allData = stroomEventLoggingService.getDataItems(
                new TestSecretObj("test", "xyzzy", "open-sesame"));

        assertThat(allData.size()).isEqualTo(3); //name property should be excluded, as this is logged elsewhere
        assertThat(allData).noneMatch(data -> data.getName().equals("name"));
        assertThat(allData).anyMatch(data -> data.getName().equals("password"));
        assertThat(allData).anyMatch(data -> data.getName().equals("myNewSecret"));
        assertThat(allData).anyMatch(data -> data.getName().equals("secret"));

        assertThat(allData).noneMatch(data -> data.getValue().equals("xyzzy"));
        assertThat(allData).noneMatch(data -> data.getValue().equals("open-sesame"));
        assertThat(allData.stream().filter(data -> data.getValue().equals("test"))
                .toList().size()).isEqualTo(0);
        assertThat(allData.stream().filter(data -> data.getValue().equals("false"))
                .toList().size()).isEqualTo(1);
    }

    @Test
    void testConvertPojoWithInfoProvider() throws Exception {
        final BaseObject baseObject = stroomEventLoggingService.convert(new TestObj());
        assertThat(baseObject).isSameAs(testObj);
    }

    @Test
    void testConvertPojoWithoutInfoProvider() throws Exception {
        final String name = "TestSecretObject1";
        final String typeName = TestSecretObj.class.getSimpleName();
        final BaseObject baseObject = stroomEventLoggingService.convert(new TestSecretObj(name, "b", "x"));

        assertThat(baseObject.getType()).isEqualTo(typeName);

        final String description = baseObject.getDescription();
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

        private final String name;
        private final String password;
        private String myNewSecret;
        private boolean secret;

        public TestSecretObj(final String name, final String password, final String myNewSecret) {
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

        @Override
        public String getName() {
            return name;
        }

        public Boolean isSecret() {
            return secret;
        }
    }
}
