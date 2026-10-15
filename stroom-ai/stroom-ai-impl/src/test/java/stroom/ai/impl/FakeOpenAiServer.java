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

package stroom.ai.impl;

import stroom.util.json.JsonUtil;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;

/// An OpenAI-compatible chat completions server running in-process on a random port, for testing a whole agent
/// run through the real model client, HTTP client and streaming. It streams the replies it is given, in order
/// (an answer, or a call of a tool), as server-sent events, and records each request it receives.
final class FakeOpenAiServer implements AutoCloseable {

    private static final String DEFAULT_ANSWER = "Done.";

    private final HttpServer server;
    private final Deque<Map<String, Object>> replies = new ConcurrentLinkedDeque<>();
    private final List<Map<String, Object>> requests = Collections.synchronizedList(new ArrayList<>());

    private FakeOpenAiServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", this::handle);
        server.start();
    }

    /// Starts a server.
    static FakeOpenAiServer start() throws IOException {
        return new FakeOpenAiServer();
    }

    /// @return The base URL to give the model client.
    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    /// Adds an answer to the replies, streamed a word at a time.
    FakeOpenAiServer answer(final String text) {
        replies.add(Map.of("answer", text));
        return this;
    }

    /// Adds a call of a tool to the replies.
    FakeOpenAiServer callTool(final String name, final String argumentsJson) {
        replies.add(Map.of("tool", name, "arguments", argumentsJson));
        return this;
    }

    /// @return The body of every request received so far, in order.
    List<Map<String, Object>> requests() {
        synchronized (requests) {
            return List.copyOf(requests);
        }
    }

    @SuppressWarnings("unchecked")
    private void handle(final HttpExchange exchange) throws IOException {
        try (exchange) {
            final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(JsonUtil.readValue(body, Map.class));
            final Map<String, Object> reply = replies.isEmpty()
                    ? Map.of("answer", DEFAULT_ANSWER)
                    : replies.poll();
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            final OutputStream out = exchange.getResponseBody();
            if (reply.containsKey("tool")) {
                send(out, chunk(Map.of("role", "assistant", "tool_calls", List.of(Map.of(
                        "index", 0,
                        "id", "call_" + requests.size(),
                        "type", "function",
                        "function", Map.of("name", reply.get("tool"), "arguments", reply.get("arguments"))))),
                        null));
                send(out, chunk(Map.of(), "tool_calls"));
            } else {
                final String[] words = ((String) reply.get("answer")).split(" ");
                for (int i = 0; i < words.length; i++) {
                    send(out, chunk(Map.of("content", (i == 0
                            ? ""
                            : " ") + words[i]), null));
                }
                send(out, chunk(Map.of(), "stop"));
            }
            out.write("data: [DONE]\n\n".getBytes(StandardCharsets.UTF_8));
            out.flush();
        }
    }

    private static Map<String, Object> chunk(final Map<String, Object> delta, final String finishReason) {
        final Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", finishReason);
        final Map<String, Object> chunk = new HashMap<>();
        chunk.put("id", "c1");
        chunk.put("object", "chat.completion.chunk");
        chunk.put("model", "fake");
        chunk.put("choices", List.of(choice));
        if (finishReason != null) {
            chunk.put("usage", Map.of("prompt_tokens", 10, "completion_tokens", 5, "total_tokens", 15));
        }
        return chunk;
    }

    private static void send(final OutputStream out, final Map<String, Object> chunk) throws IOException {
        out.write(("data: " + JsonUtil.writeValueAsString(chunk, false) + "\n\n").getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
