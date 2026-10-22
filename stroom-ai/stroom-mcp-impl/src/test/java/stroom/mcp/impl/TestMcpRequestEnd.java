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

package stroom.mcp.impl;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TestMcpRequestEnd {

    @Test
    void theWorkOnARequestIsToldWhenTheClientsRequestEnds() {
        // E.g. the transport gave up sending to a client that went away: the work goes on for no one otherwise.
        final HttpServletRequest request = Mockito.mock(HttpServletRequest.class);
        final AsyncContext asyncContext = Mockito.mock(AsyncContext.class);
        Mockito.when(request.startAsync()).thenReturn(asyncContext);
        final ArgumentCaptor<McpRequestEnd> attribute = ArgumentCaptor.forClass(McpRequestEnd.class);

        final HttpServletRequest watched = McpRequestEnd.watch(request);
        Mockito.verify(request).setAttribute(Mockito.eq(McpRequestEnd.ATTRIBUTE), attribute.capture());
        Mockito.when(request.getAttribute(McpRequestEnd.ATTRIBUTE)).thenReturn(attribute.getValue());
        final McpRequestEnd end = McpRequestEnd.of(watched);
        final AtomicInteger told = new AtomicInteger();
        end.onEnd(told::incrementAndGet);

        assertThat(watched.startAsync()).isSameAs(asyncContext);
        Mockito.verify(asyncContext).addListener(end);
        end.onComplete(null);
        end.onTimeout(null);

        // Once, however many ways it ends; and at once for a listener that comes late.
        assertThat(told).hasValue(1);
        end.onEnd(told::incrementAndGet);
        assertThat(told).hasValue(2);
    }
}
