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

package stroom.event.logging.rs.impl;

import stroom.docref.HasUuid;
import stroom.dropwizard.common.DelegatingExceptionMapper;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.EventActionDecorator;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.impl.LoggingConfig;
import stroom.event.logging.mock.MockStroomEventLoggingService;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.util.json.JsonUtil;
import stroom.util.shared.FetchWithIntegerId;
import stroom.util.shared.HasId;
import stroom.util.shared.HasIntegerId;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Guice;
import com.google.inject.Injector;
import event.logging.ProcessAction;
import event.logging.ProcessEventAction;
import event.logging.Query;
import event.logging.SearchEventAction;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.container.ResourceContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.ext.WriterInterceptorContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

public class TestRestResourceAutoLogger {

    private static final Integer BEFORE_ID = 78910;
    private final HttpServletRequest request = new MockHttpServletRequest();

    private final MockContainerRequestContext requestContext = new MockContainerRequestContext();

    //    private final SecurityContext securityContext = new MockSecurityContext();
    @Spy
    private SecurityContext mockSecurityContextSpy = new MockSecurityContext();

    @Mock
    private DocumentEventLog documentEventLog;

    private RequestEventLog requestEventLog;

    private final StroomEventLoggingService eventLoggingService = new MockStroomEventLoggingService();

    @Mock
    private ResourceInfo resourceInfo;

    @Mock
    private ResourceContext resourceContext;

    @Mock
    private WriterInterceptorContext writerInterceptorContext;

    @Mock
    private DelegatingExceptionMapper delegatingExceptionMapper;

    private final LoggingConfig config = new LoggingConfig();

    private final TestResource testResource = new TestResource();

    @Captor
    private ArgumentCaptor<Object> objectCaptor;

    @Captor
    private ArgumentCaptor<Object> outcomeObjectCaptor;

    @Captor
    private ArgumentCaptor<String> listContentCaptor;

    @Captor
    private ArgumentCaptor<String> eventTypeIdCaptor;

    @Captor
    private ArgumentCaptor<String> verbCaptor;

    @Captor
    private ArgumentCaptor<Throwable> throwableCaptor;

    @Captor
    private ArgumentCaptor<Throwable> delgatedThrowableCaptor;

    @Captor
    private ArgumentCaptor<Query> queryCaptor;

    @Captor
    private ArgumentCaptor<PageResponse> pageResponseCaptor;

    @Captor
    private ArgumentCaptor<EventActionDecorator<?>> eventActionDecoratorArgumentCaptor;

    RestResourceAutoLoggerImpl filter;

    ObjectMapper objectMapper;

    Random random = new Random();

    private final Injector injector;

    private AutoCloseable closeable;

    TestRestResourceAutoLogger() {
        injector = Guice.createInjector(new MockRsLoggingModule());
    }

    @Test
    public void testLogBasic() throws Exception {
        final Method method = TestResource.class.getMethod("fetch", Integer.class);

        //Set up resource and method
        Mockito.doReturn(TestResource.class).when(resourceInfo).getResourceClass();

        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        filter.filter(requestContext);
        filter.aroundWriteTo(writerInterceptorContext);

        Mockito.verify(documentEventLog).view(objectCaptor.capture(), eventTypeIdCaptor.capture(), verbCaptor.capture(),
                throwableCaptor.capture());

        final Object loggedObject = objectCaptor.getValue();
        final String eventTypeId = eventTypeIdCaptor.getValue();
        final String descriptionVerb = verbCaptor.getValue();
        final Throwable exception = throwableCaptor.getValue();

        assertThat(loggedObject).isNull();
        assertThat(eventTypeId)
                .isEqualTo("TestResource.fetch");
        assertThat(descriptionVerb).isNull();
        assertThat(exception).isNull();
    }

    @Test
    public void testLogAlternativeLogMethod() throws Exception {
        final Method method = TestResource.class.getMethod("findAndDestroy");

        //Set up resource and method
        Mockito.doReturn(TestResource.class).when(resourceInfo).getResourceClass();

        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        filter.filter(requestContext);
        filter.aroundWriteTo(writerInterceptorContext);

        Mockito.verify(documentEventLog).delete(objectCaptor.capture(),
                eventTypeIdCaptor.capture(),
                verbCaptor.capture(),
                throwableCaptor.capture());

        final Object loggedObject = objectCaptor.getValue();
        final String eventTypeId = eventTypeIdCaptor.getValue();
        final String descriptionVerb = verbCaptor.getValue();
        final Throwable exception = throwableCaptor.getValue();

        assertThat(loggedObject).isNull();
        assertThat(eventTypeId)
                .isEqualTo("TestResource.findAndDestroy");
        assertThat(descriptionVerb).isNull();
        assertThat(exception).isNull();
    }

    @Test
    public void testLogWithEntity() throws Exception {
        final Method method = TestResource.class.getMethod("create", TestObj.class);

        //Set up resource and method
        Mockito.doReturn(TestResource.class)
                .when(resourceInfo).getResourceClass();
        Mockito.when(resourceInfo.getResourceMethod())
                .thenReturn(method);

        final int run = random.nextInt(1000);
        final Integer requestId = run;
        final Integer responseId = -run;

        final TestObj requestTestObj = new TestObj(requestId);
        //Set up Stream containing serialized object

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        objectMapper.writeValue(bos, requestTestObj);
        bos.flush();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
        requestContext.setEntityStream(byteArrayInputStream);

        final TestObj responseTestObj = new TestObj(responseId);
        Mockito.when(writerInterceptorContext.getEntity())
                .thenReturn(responseTestObj);

        filter.filter(requestContext);
        filter.aroundWriteTo(writerInterceptorContext);

        Mockito.verify(documentEventLog).create(objectCaptor.capture(),
                eventTypeIdCaptor.capture(),
                verbCaptor.capture(),
                throwableCaptor.capture());

        final Object loggedObject = objectCaptor.getValue();
        final String eventTypeId = eventTypeIdCaptor.getValue();
        final String descriptionVerb = verbCaptor.getValue();
        final Throwable exception = throwableCaptor.getValue();

        assertThat(loggedObject).isInstanceOf(TestObj.class);
        final TestObj entity = (TestObj) loggedObject;
        assertThat(entity.getId())
                .isEqualTo(responseId);

        assertThat(eventTypeId)
                .isEqualTo("TestResource.create");
        assertThat(descriptionVerb).isNull();
        assertThat(exception).isNull();
    }

    @Test
    public void testLogWithEntityAndAlternativeDescription() throws Exception {
        final Method method = TestResource.class.getMethod("update", TestObj.class);

        //Set up resource and method
        Mockito.doReturn(TestResource.class)
                .when(resourceInfo).getResourceClass();
        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        final int run = random.nextInt(1000);
        final Integer requestId = run;
        final Integer responseId = -run;

        //Set up Stream containing serialized object

        //Set up Stream containing serialized object
        final String idAsStr = Integer.toString(requestId);
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(idAsStr.getBytes());
        bos.flush();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
        requestContext.setEntityStream(byteArrayInputStream);

        final TestObj responseTestObj = new TestObj(responseId);
        Mockito.when(writerInterceptorContext.getEntity())
                .thenReturn(responseTestObj);

        filter.filter(requestContext);
        filter.aroundWriteTo(writerInterceptorContext);

        Mockito.verify(documentEventLog).update(objectCaptor.capture(), outcomeObjectCaptor.capture(),
                eventTypeIdCaptor.capture(), verbCaptor.capture(),
                throwableCaptor.capture());

        final Object beforeLoggedObject = objectCaptor.getValue();
        assertThat(beforeLoggedObject).isInstanceOf(TestObj.class);

        final TestObj testObj = (TestObj) beforeLoggedObject;
        assertThat(testObj.getId())
                .isEqualTo(BEFORE_ID);


        final Object afterLoggedObject = outcomeObjectCaptor.getValue();
        final String eventTypeId = eventTypeIdCaptor.getValue();
        final String descriptionVerb = verbCaptor.getValue();
        final Throwable exception = throwableCaptor.getValue();

        assertThat(afterLoggedObject).isInstanceOf(TestObj.class);
        final TestObj entity = (TestObj) afterLoggedObject;
        assertThat(entity.getId())
                .isEqualTo(responseId);

        assertThat(eventTypeId)
                .isEqualTo("TestingUpdate");
        assertThat(descriptionVerb)
                .isEqualTo("Testing");
        assertThat(exception).isNull();
    }


    @Test
    public void testLogWithUuidAndActionDecorator() throws Exception {
        final Method method = TestResource.class.getMethod("shutdown", String.class);

        //Set up resource and method
        Mockito.doReturn(TestResource.class).when(resourceInfo).getResourceClass();
        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        final String uuid = "ABC-175-3454-A5-123";

        final String response = "Simulated shutdown complete";
        //Set up Stream containing serialized object

        //Set up Stream containing serialized object
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(uuid.getBytes());
        bos.flush();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
        requestContext.setEntityStream(byteArrayInputStream);


        ((MockUriInfo) requestContext.getUriInfo()).setUuid(uuid);

        Mockito.when(writerInterceptorContext.getEntity()).thenReturn(response);

        filter.filter(requestContext);
        filter.aroundWriteTo(writerInterceptorContext);

        Mockito.verify(documentEventLog).process(
                objectCaptor.capture(),
                eventTypeIdCaptor.capture(), verbCaptor.capture(),
                throwableCaptor.capture(),
                (EventActionDecorator<ProcessEventAction>) eventActionDecoratorArgumentCaptor.capture());

        final Object loggedObject = objectCaptor.getValue();

        final String eventTypeId = eventTypeIdCaptor.getValue();
        final String descriptionVerb = verbCaptor.getValue();
        final Throwable exception = throwableCaptor.getValue();
        final EventActionDecorator<?> eventActionDecorator = eventActionDecoratorArgumentCaptor.getValue();

        assertThat(eventActionDecorator).isInstanceOf(TestResource.TestShutdownEventActionDecorator.class);
        assertThat(loggedObject).isInstanceOf(HasUuid.class);
        assertThat(uuid)
                .isEqualTo(((HasUuid) loggedObject).getUuid());

        assertThat(exception).isNull();
        assertThat(descriptionVerb)
                .isEqualTo("Shutting down");

        assertThat(eventTypeId)
                .isEqualTo("TestResource.shutdown");

    }


    @Test
    public void testLogWithException() throws Exception {
        final Method method = TestResource.class.getMethod("random", Integer.class);

        //Set up resource and method
        Mockito.doReturn(TestResource.class).when(resourceInfo).getResourceClass();
        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        final int pathId = random.nextInt();

        //Set up Stream containing serialized object
        final String idAsStr = Integer.toString(pathId);
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(idAsStr.getBytes());
        bos.flush();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
        requestContext.setEntityStream(byteArrayInputStream);

        ((MockUriInfo) requestContext.getUriInfo()).setId(pathId);

        final String message = "Testing exception handling";
        final Exception testException = new Exception(message);

        filter.filter(requestContext);
        filter.toResponse(testException);

        Mockito.verify(documentEventLog).unknownOperation(objectCaptor.capture(),
                eventTypeIdCaptor.capture(),
                verbCaptor.capture(),
                throwableCaptor.capture());

        Mockito.verify(delegatingExceptionMapper).toResponse(delgatedThrowableCaptor.capture());

        assertThat(delgatedThrowableCaptor.getValue()).hasMessage(message);

        final Object loggedObject = objectCaptor.getValue();
        final String eventTypeId = eventTypeIdCaptor.getValue();
        final String descriptionVerb = verbCaptor.getValue();
        final Throwable exception = throwableCaptor.getValue();

        assertThat(loggedObject).isInstanceOf(HasId.class);

        final HasId hasId = (HasId) loggedObject;
        assertThat(hasId.getId())
                .isEqualTo(pathId);

        assertThat(eventTypeId)
                .isEqualTo("TestResource.random");
        assertThat(descriptionVerb).isNotEmpty();
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage())
                .isEqualTo(message);
    }

    @Test
    public void testLogSearch() throws Exception {
        final Method method = TestResource.class.getMethod("find", Integer.class, TestObj.class);

        //Set up resource and method
        Mockito.doReturn(TestResource.class).when(resourceInfo).getResourceClass();
        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        final int run = random.nextInt(1000);
        final Integer requestId = run;
        final Integer responseId = -run;

        final TestObj requestTestObj = new TestObj(requestId);
        final TestObj responseTestObj = new TestObj(responseId);

        final ResultPage<TestObj> resultPage = new ResultPage<>(List.of(responseTestObj));

        //Set up Stream containing serialized object

        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        objectMapper.writeValue(bos, requestTestObj);
        bos.flush();

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
        requestContext.setEntityStream(byteArrayInputStream);
        Mockito.when(writerInterceptorContext.getEntity()).thenReturn(resultPage);

        filter.filter(requestContext);
        filter.aroundWriteTo(writerInterceptorContext);

        Mockito.verify(documentEventLog).search(eventTypeIdCaptor.capture(), queryCaptor.capture(),
                listContentCaptor.capture(),
                pageResponseCaptor.capture(), verbCaptor.capture(), throwableCaptor.capture(),
                (EventActionDecorator<SearchEventAction>) eventActionDecoratorArgumentCaptor.capture());


//        search(final String typeId, final Query query, final String resultType, final PageResponse pageResponse,
//        final String verb, final Throwable ex);
//                (typeId, query, listContents, pageResponse, descriptionVerb, erro
//                (objectCaptor.capture(), eventTypeIdCaptor.capture(), verbCaptor.capture(),
//                throwableCaptor.capture());

        final String eventTypeId = eventTypeIdCaptor.getValue();
        final Query query = queryCaptor.getValue();
        final String listContents = listContentCaptor.getValue();
        final PageResponse pageResponse = pageResponseCaptor.getValue();
        final String descriptionVerb = verbCaptor.getValue();
        final Throwable exception = throwableCaptor.getValue();

        assertThat(eventActionDecoratorArgumentCaptor.getValue()).isNull();
        assertThat(pageResponse).isNotNull();
        assertThat(pageResponse.getLength()).isEqualTo(1);

        assertThat(listContents).isEqualTo("TestObjs");
        assertThat(eventTypeId).isEqualTo("TestResource.find");
        assertThat(descriptionVerb)
                .isNull();
        assertThat(exception)
                .isNull();

        assertThat(query.getRaw()).isNotEmpty();

        TestObj deserialised = null;

        try {
            deserialised = objectMapper.readValue(query.getRaw().getBytes(), TestObj.class);
        } catch (final Exception e) {
            // Ignore any error
        }

        assertThat(deserialised).isNotNull();
        assertThat(deserialised.getId())
                .isEqualTo(requestId);

    }

    @BeforeEach
    void setup() {
        objectMapper = JsonUtil.getMapper();
        closeable = MockitoAnnotations.openMocks(this);
        requestEventLog = new RequestEventLogImpl(injector,
                config,
                documentEventLog,
                mockSecurityContextSpy,
                eventLoggingService);

        Mockito.when(resourceContext.getResource(Mockito.any())).thenReturn(testResource);

        Mockito.when(mockSecurityContextSpy.isProcessingUser())
                .thenReturn(false);

        filter = new RestResourceAutoLoggerImpl(
                () -> mockSecurityContextSpy,
                () -> requestEventLog,
                () -> config,
                resourceInfo,
                request,
                () -> delegatingExceptionMapper);
        filter.setResourceContext(resourceContext);
    }

    @AfterEach
    void reset() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    public static class TestObj implements Serializable, HasIntegerId {

        @JsonProperty
        private Integer id;

        public TestObj(final Integer id) {
            this.id = id;
        }

        public TestObj() {
        }

        @Override
        public Integer getId() {
            return id;
        }

        public void setId(final Integer id) {
            this.id = id;
        }
    }

    @AutoLogged
    public static class TestResource implements FetchWithIntegerId<TestObj> {

        public String find(@PathParam("id") final Integer id, final TestObj query) {
            return null;
        }

        public String random(@PathParam("id") final Integer id) {
            return null;
        }

        public String create(final TestObj testObj) {
            return null;
        }

        @Override
        public TestObj fetch(final Integer id) {
            return new TestObj(BEFORE_ID);
        }


        @AutoLogged(
                value = OperationType.PROCESS,
                verb = "Shutting down",
                decorator = TestShutdownEventActionDecorator.class)
        public String shutdown(@PathParam("uuid") final String uuid) {
            return null;
        }

        @AutoLogged(typeId = "TestingUpdate", verb = "Testing")
        public String update(final TestObj orig) {
            return null;
        }

        @AutoLogged(value = OperationType.DELETE)
        public void findAndDestroy() {
        }

        static class TestShutdownEventActionDecorator implements EventActionDecorator<ProcessEventAction> {

            @Override
            public ProcessEventAction decorate(final ProcessEventAction eventAction) {
                return eventAction.newCopyBuilder()
                        .withAction(ProcessAction.SHUTDOWN)
                        .build();
            }
        }
    }
}
