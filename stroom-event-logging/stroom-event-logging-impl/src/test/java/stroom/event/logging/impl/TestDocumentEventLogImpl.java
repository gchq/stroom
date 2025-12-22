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
    void setup() {
        documentEventLog = new DocumentEventLogImpl(loggingService, securityContext);
    }


}
