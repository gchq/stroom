package stroom.rs.logging.impl;

import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.IndexVolume;
import stroom.rs.logging.api.StroomServerLoggingFilter;
import stroom.util.shared.EventLogged;
import stroom.util.shared.ResultPage;
import stroom.util.shared.StroomLoggingOperationType;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.Guice;
import com.google.inject.Injector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.WriterInterceptorContext;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutput;
import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

public class TestStroomServerLoggingFilter {
    private HttpServletRequest request = new MockHttpServletRequest();

    private ContainerRequestContext requestContext = new MockContainerRequestContext();

    @Mock
    private RequestEventLog requestEventLog;

    @Mock
    private ResourceInfo resourceInfo;

    @Mock
    private WriterInterceptorContext writerInterceptorContext;

    private RequestLoggingConfig config = new RequestLoggingConfig();

    @Captor
    private ArgumentCaptor<RequestInfo> requestInfo;

    @Captor
    private ArgumentCaptor<Object> responseEntity;

    StroomServerLoggingFilterImpl filter;

    ObjectMapper objectMapper;

    Random random = new Random();

    private Injector injector;

    TestStroomServerLoggingFilter(){
        injector = Guice.createInjector(new MockRSLoggingModule());
    }

    @Test
    public void testLogBasic() throws Exception {
        Method method = TestResource.class.getMethod("read",Integer.class);

        //Set up resource and method
        Mockito.doReturn(TestResource.class).when(resourceInfo).getResourceClass();

        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        filter.filter(requestContext);
        filter.aroundWriteTo(writerInterceptorContext);


        Mockito.verify(requestEventLog).log(requestInfo.capture(),responseEntity.capture());

        StroomLoggingOperationType operationType = requestInfo.getValue().getContainerResourceInfo().getOperationType();

        assertThat(StroomLoggingOperationType.VIEW).isEqualTo(operationType);
    }

    @Test
    public void testLogWithEntity() throws Exception {
        Method method = TestResource.class.getMethod("create",TestObj.class);

        //Set up resource and method
        Mockito.doReturn(TestResource.class).when(resourceInfo).getResourceClass();
        Mockito.when(resourceInfo.getResourceMethod()).thenReturn(method);

        int run = random.nextInt();
        String requestId = "request-" + run;
        String responseId = "response-" + run;

        TestObj requestTestObj = new TestObj(requestId);
        TestObj responseTestObj = new TestObj(responseId);
        //Set up Stream containing serialized object

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        objectMapper.writeValue(bos, requestTestObj);
        bos.flush();

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bos.toByteArray());
        requestContext.setEntityStream(byteArrayInputStream);
        Mockito.when(writerInterceptorContext.getEntity()).thenReturn(responseTestObj);

        filter.filter(requestContext);
        filter.aroundWriteTo(writerInterceptorContext);

        Mockito.verify(requestEventLog).log(requestInfo.capture(),responseEntity.capture());

        StroomLoggingOperationType operationType = requestInfo.getValue().getContainerResourceInfo().getOperationType();
        Object responseObj = responseEntity.getValue();
        assertThat(responseObj).isInstanceOf(TestObj.class);
        if (responseObj instanceof TestObj){
            TestObj entity = (TestObj) responseObj;
            assertThat(entity.getId()).isEqualTo(responseId);
        }
        Object requestObj = requestInfo.getValue();
        assertThat(requestObj).isInstanceOf(RequestInfo.class);
        if (requestObj instanceof RequestInfo){
            RequestInfo reqInfo = (RequestInfo) requestObj;
            Object requestEntity = reqInfo.getRequestObj();
            assertThat(requestEntity).isInstanceOf(TestObj.class);
            if (requestEntity instanceof TestObj){
                TestObj entity = (TestObj) requestEntity;
                assertThat(entity.getId()).isEqualTo(requestId);
            }
        }

        assertThat(StroomLoggingOperationType.CREATE).isEqualTo(operationType);
    }

    @BeforeEach
    void setup(){
        // Handle all the mock creation and injection.
        MockitoAnnotations.initMocks(this);
        filter = new StroomServerLoggingFilterImpl(requestEventLog, config, resourceInfo, request);
        objectMapper = createObjectMapper();
    }


    private static ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }

    public static class TestObj implements Serializable {
        @JsonProperty
        private String id;

        public TestObj(String id){
            this.id = id;
        }
        
        public TestObj(){
        }

        public String getId() {
            return id;
        }

        public void setId(final String id) {
            this.id = id;
        }
    }


    @EventLogged
    public static class TestResource {

        public String getCall() {
            return null;
        }

        public String find() {
            return null;
        }

        public String create(TestObj testObj) {
            return null;
        }

        public String read(final Integer id) {
            return null;
        }

    }

}