/*
 * Copyright 2020 Crown Copyright
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

import stroom.dropwizard.common.BasicExceptionMapper;
import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.mock.MockStroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;
import stroom.util.shared.AutoLogged;
import stroom.util.shared.AutoLogged.OperationType;
import stroom.util.shared.HasId;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.Guice;
import com.google.inject.Injector;
import event.logging.Query;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Random;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.PathParam;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.ext.WriterInterceptorContext;

import static org.assertj.core.api.Assertions.assertThat;

public class TestRestResourceAutoLogger {

    RestResourceAutoLoggerImpl filter;
    ObjectMapper objectMapper;
    Random random = new Random();
    private final HttpServletRequest request = new MockHttpServletRequest();
    private final MockContainerRequestContext requestContext = new MockContainerRequestContext();
    private final SecurityContext securityContext = new MockSecurityContext();
    @Mock
    private DocumentEventLog documentEventLog;
    private RequestEventLog requestEventLog;
    private final StroomEventLoggingService eventLoggingService = new MockStroomEventLoggingService();
    @Mock
    private ResourceInfo resourceInfo;
    @Mock
    private WriterInterceptorContext writerInterceptorContext;
    private final RequestLoggingConfig config = new RequestLoggingConfig();
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
    private ArgumentCaptor<Query> queryCaptor;
    @Captor
    private ArgumentCaptor<PageResponse> pageResponseCaptor;
    private Injector injector;

    private AutoCloseable closeable;

    TestRestResourceAutoLogger() {
        injector = Guice.createInjector(new MockRsLoggingModule());
    }

    private static ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }

    @Test
    public void testLogBasic() throws Exception {
        Method method = TestResource.class.getMethod("read", Integer.class);

        //Set up resource and method
        Mockito.doReturn(TestResource.class).when(resourceInfo).getResourceClass();

        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        filter.filter(requestContext);
        filter.aroundWriteTo(writerInterceptorContext);

        Mockito.verify(documentEventLog)
                .view(
                        objectCaptor.capture(),
                        eventTypeIdCaptor.capture(),
                        verbCaptor.capture(),
                        throwableCaptor.capture());

        Object loggedObject = objectCaptor.getValue();
        String eventTypeId = eventTypeIdCaptor.getValue();
        String descriptionVerb = verbCaptor.getValue();
        Throwable exception = throwableCaptor.getValue();

        assertThat(loggedObject).isNull();
        assertThat(eventTypeId).isEqualTo("TestResource.read");
        assertThat(descriptionVerb).isNull();
        assertThat(exception).isNull();
    }

    @Test
    public void testLogAlternativeLogMethod() throws Exception {
        Method method = TestResource.class.getMethod("findAndDestroy");

        //Set up resource and method
        Mockito.doReturn(TestResource.class).when(resourceInfo).getResourceClass();

        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        filter.filter(requestContext);
        filter.aroundWriteTo(writerInterceptorContext);

        Mockito.verify(documentEventLog)
                .delete(
                        objectCaptor.capture(),
                        eventTypeIdCaptor.capture(),
                        verbCaptor.capture(),
                        throwableCaptor.capture());

        Object loggedObject = objectCaptor.getValue();
        String eventTypeId = eventTypeIdCaptor.getValue();
        String descriptionVerb = verbCaptor.getValue();
        Throwable exception = throwableCaptor.getValue();

        assertThat(loggedObject).isNull();
        assertThat(eventTypeId).isEqualTo("TestResource.findAndDestroy");
        assertThat(descriptionVerb).isNull();
        assertThat(exception).isNull();
    }

    @Test
    public void testLogWithEntity() throws Exception {
        Method method = TestResource.class.getMethod("create", TestObj.class);

        //Set up resource and method
        Mockito.doReturn(TestResource.class).when(resourceInfo).getResourceClass();
        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        int run = random.nextInt();
        String requestId = "request-" + run;
        String responseId = "response-" + run;

        final TestObj requestTestObj = new TestObj(requestId);
        final TestObj responseTestObj = new TestObj(responseId);
        //Set up Stream containing serialized object

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        objectMapper.writeValue(bos, requestTestObj);
        bos.flush();

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
        requestContext.setEntityStream(byteArrayInputStream);
        Mockito.when(writerInterceptorContext.getEntity()).thenReturn(responseTestObj);

        filter.filter(requestContext);
        filter.aroundWriteTo(writerInterceptorContext);

        Mockito.verify(documentEventLog).create(objectCaptor.capture(),
                eventTypeIdCaptor.capture(),
                verbCaptor.capture(),
                throwableCaptor.capture());

        Object loggedObject = objectCaptor.getValue();
        String eventTypeId = eventTypeIdCaptor.getValue();
        String descriptionVerb = verbCaptor.getValue();
        Throwable exception = throwableCaptor.getValue();

        assertThat(loggedObject).isInstanceOf(TestObj.class);
        TestObj entity = (TestObj) loggedObject;
        assertThat(entity.getId()).isEqualTo(responseId);

        assertThat(eventTypeId).isEqualTo("TestResource.create");
        assertThat(descriptionVerb).isNull();
        assertThat(exception).isNull();
    }

    @Test
    public void testLogWithEntityAndAlternativeDescription() throws Exception {
        Method method = TestResource.class.getMethod("update", TestObj.class);

        //Set up resource and method
        Mockito.doReturn(TestResource.class).when(resourceInfo).getResourceClass();
        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        int run = random.nextInt();
        String requestId = "request-" + run;
        String responseId = "response-" + run;

        final TestObj requestTestObj = new TestObj(requestId);
        final TestObj responseTestObj = new TestObj(responseId);
        //Set up Stream containing serialized object

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        objectMapper.writeValue(bos, requestTestObj);
        bos.flush();

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
        requestContext.setEntityStream(byteArrayInputStream);
        Mockito.when(writerInterceptorContext.getEntity()).thenReturn(responseTestObj);

        filter.filter(requestContext);
        filter.aroundWriteTo(writerInterceptorContext);

        Mockito.verify(documentEventLog).update(objectCaptor.capture(), outcomeObjectCaptor.capture(),
                eventTypeIdCaptor.capture(), verbCaptor.capture(),
                throwableCaptor.capture());

        Object loggedObject = objectCaptor.getValue();
        Object afterLoggedObject = outcomeObjectCaptor.getValue();
        final String eventTypeId = eventTypeIdCaptor.getValue();
        final String descriptionVerb = verbCaptor.getValue();
        final Throwable exception = throwableCaptor.getValue();

        assertThat(loggedObject).isInstanceOf(TestObj.class);

        TestObj entity = (TestObj) loggedObject;
        assertThat(entity.getId()).isEqualTo(requestId);

        assertThat(afterLoggedObject).isInstanceOf(TestObj.class);
        entity = (TestObj) afterLoggedObject;
        assertThat(entity.getId()).isEqualTo(responseId);

        assertThat(eventTypeId).isEqualTo("TestingUpdate");
        assertThat(descriptionVerb).isEqualTo("Testing");
        assertThat(exception).isNull();
    }

    @Test
    public void testLogWithException() throws Exception {
        Method method = TestResource.class.getMethod("random", Integer.class);

        //Set up resource and method
        Mockito.doReturn(TestResource.class).when(resourceInfo).getResourceClass();
        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        int pathId = random.nextInt();

        //Set up Stream containing serialized object
        String idAsStr = Integer.toString(pathId);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bos.write(idAsStr.getBytes());
        bos.flush();

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
        requestContext.setEntityStream(byteArrayInputStream);

        ((MockUriInfo) requestContext.getUriInfo()).setId(pathId);

        String message = "Testing exception handling";
        Exception testException = new Exception(message);

        filter.filter(requestContext);
        filter.toResponse(testException);

        Mockito.verify(documentEventLog).unknownOperation(objectCaptor.capture(),
                eventTypeIdCaptor.capture(),
                verbCaptor.capture(),
                throwableCaptor.capture());

        Object loggedObject = objectCaptor.getValue();
        String eventTypeId = eventTypeIdCaptor.getValue();
        String descriptionVerb = verbCaptor.getValue();
        Throwable exception = throwableCaptor.getValue();

        assertThat(loggedObject).isInstanceOf(HasId.class);

        HasId hasId = (HasId) loggedObject;
        assertThat(hasId.getId()).isEqualTo(pathId);

        assertThat(eventTypeId).isEqualTo("TestResource.random");
        assertThat(descriptionVerb).isNotEmpty();
        assertThat(exception).isNotNull();
        assertThat(exception.getMessage()).isEqualTo(message);
    }

    @Test
    public void testLogSearch() throws Exception {
        Method method = TestResource.class.getMethod("find", Integer.class, TestObj.class);

        //Set up resource and method
        Mockito.doReturn(TestResource.class).when(resourceInfo).getResourceClass();
        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        int run = random.nextInt();
        String requestId = "request-" + run;
        String responseId = "response-" + run;

        TestObj requestTestObj = new TestObj(requestId);
        TestObj responseTestObj = new TestObj(responseId);

        final ResultPage<TestObj> resultPage = new ResultPage<>(List.of(responseTestObj));

        //Set up Stream containing serialized object

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        objectMapper.writeValue(bos, requestTestObj);
        bos.flush();

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
        requestContext.setEntityStream(byteArrayInputStream);
        Mockito.when(writerInterceptorContext.getEntity()).thenReturn(resultPage);

        filter.filter(requestContext);
        filter.aroundWriteTo(writerInterceptorContext);

        Mockito.verify(documentEventLog).search(eventTypeIdCaptor.capture(), queryCaptor.capture(),
                listContentCaptor.capture(),
                pageResponseCaptor.capture(), verbCaptor.capture(), throwableCaptor.capture());


//        search(final String typeId, final Query query, final String resultType, final PageResponse pageResponse, final String verb, final Throwable ex);
//                (typeId, query, listContents, pageResponse, descriptionVerb, erro
//                (objectCaptor.capture(), eventTypeIdCaptor.capture(), verbCaptor.capture(),
//                throwableCaptor.capture());

        String eventTypeId = eventTypeIdCaptor.getValue();
        Query query = queryCaptor.getValue();
        String listContents = listContentCaptor.getValue();
        PageResponse pageResponse = pageResponseCaptor.getValue();
        String descriptionVerb = verbCaptor.getValue();
        Throwable exception = throwableCaptor.getValue();

        assertThat(pageResponse).isNotNull();
        assertThat(pageResponse.getLength()).isEqualTo(1);

        assertThat(listContents).isEqualTo("TestObjs");
        assertThat(eventTypeId).isEqualTo("TestResource.find");
        assertThat(descriptionVerb).isNull();
        assertThat(exception).isNull();

        assertThat(query.getRaw()).isNotEmpty();

        TestObj deserialised = null;

        try {
            deserialised = objectMapper.readValue(query.getRaw().getBytes(), TestObj.class);
        } catch (Exception e) {
            // Ignore errors
        }

        assertThat(deserialised).isNotNull();
        assertThat(deserialised.getId()).isEqualTo(requestId);

    }

    @BeforeEach
    void setup() {
        objectMapper = createObjectMapper();
        closeable = MockitoAnnotations.openMocks(this);
        requestEventLog = new RequestEventLogImpl(config, documentEventLog, securityContext, eventLoggingService);

        filter = new RestResourceAutoLoggerImpl(
                requestEventLog,
                config,
                resourceInfo,
                request,
                new BasicExceptionMapper());
    }

    @AfterEach
    void reset() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
    }

    public static class TestObj implements Serializable {

        @JsonProperty
        private String id;

        public TestObj(String id) {
            this.id = id;
        }

        public TestObj() {
        }

        public String getId() {
            return id;
        }

        public void setId(final String id) {
            this.id = id;
        }
    }


    @AutoLogged
    public static class TestResource {

        public String find(@PathParam("id") Integer id, TestObj query) {
            return null;
        }

        public String random(@PathParam("id") Integer id) {
            return null;
        }

        public String create(TestObj testObj) {
            return null;
        }

        public String read(final Integer id) {
            return null;
        }

        @AutoLogged(typeId = "TestingUpdate", verb = "Testing")
        public String update(TestObj testObj) {
            return null;
        }

        @AutoLogged(value = OperationType.DELETE)
        public void findAndDestroy() {
        }
    }

}
