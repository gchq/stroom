/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.servlet;

import stroom.proxy.repo.queue.QueueMonitors;
import stroom.proxy.repo.store.FileStores;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestProxyQueueMonitoringServlet {

    @Test
    void pageDeclaresItsLanguage() throws Exception {
        // The page says it is in English, for screen readers (gchq/stroom#5408).
        final QueueMonitors queueMonitors = mock(QueueMonitors.class);
        when(queueMonitors.log()).thenReturn("");
        final FileStores fileStores = mock(FileStores.class);
        when(fileStores.log()).thenReturn("");
        final HttpServletResponse response = mock(HttpServletResponse.class);
        final StringWriter page = new StringWriter();
        final PrintWriter printWriter = new PrintWriter(page);
        when(response.getWriter()).thenReturn(printWriter);

        new ProxyQueueMonitoringServlet(() -> queueMonitors, () -> fileStores)
                .doGet(mock(HttpServletRequest.class), response);
        printWriter.flush();

        assertThat(page.toString()).startsWith("<html lang=\"en\">");
    }
}
