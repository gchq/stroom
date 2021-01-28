package stroom.event.logging.impl;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.security.api.SecurityContext;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;

public class TestDocumentEventLogImpl {
    @Mock
    private StroomEventLoggingService loggingService;

    private final SecurityContext securityContext = new MockSecurityContext();

    private DocumentEventLogImpl documentEventLog;

    @BeforeEach
    void setup (){
       documentEventLog = new DocumentEventLogImpl(loggingService, securityContext);
    }


}
