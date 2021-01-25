package stroom.event.logging.impl;

import stroom.event.logging.api.ObjectInfoProvider;
import stroom.event.logging.api.ObjectType;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;

import com.fasterxml.jackson.annotation.JsonProperty;
import event.logging.Data;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import javax.inject.Provider;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class TestDocumentEventLogImpl {
    @Mock
    private StroomEventLoggingService loggingService;

    private final SecurityContext securityContext = new MockSecurityContext();

    private final Map<ObjectType, Provider<ObjectInfoProvider>> objectInfoProviderMap = new HashMap<>();

    private DocumentEventLogImpl documentEventLog;

    @BeforeEach
    void setup (){
       documentEventLog = new DocumentEventLogImpl(loggingService, objectInfoProviderMap, securityContext);
    }

    @Test
    void testDataItemCreationAndRedaction () throws Exception {
        List<Data> allData = documentEventLog.getDataItems(new TestObj("test", "xyzzy", "open-sesame"));

        assertThat(allData.size()).isEqualTo(3);
        assertThat(allData).anyMatch(data -> data.getName().equals("name"));
        assertThat(allData).anyMatch(data -> data.getName().equals("password"));
        assertThat(allData).anyMatch(data -> data.getName().equals("myNewSecret"));

        assertThat(allData).noneMatch(data -> data.getValue().equals("xyzzy"));
        assertThat(allData).noneMatch(data -> data.getValue().equals("open-sesame"));
        assertThat(allData.stream().filter(data -> data.getValue().equals("test"))
                .collect(Collectors.toList()).size()).isEqualTo(1);
    }


    public static class TestObj {
        private String name;
        private String password;
        private String myNewSecret;

        public TestObj (String name, String password, String myNewSecret){
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
    }


}
