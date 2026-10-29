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

/// The chat model, as an OpenAI-compatible chat completions server running in-process on a random port, so that a
/// chat goes through Stroom's real model client, HTTP client and streaming. It is scripted: each request that
/// offers tools (the agent's turns) gets the next of the replies it is given (an answer, or a call of a tool),
/// streamed as server-sent events; any other request (e.g. naming the chat, or suggesting follow-ups) is answered
/// with a short answer of its own, so that it doesn't take the agent's replies. Every request is recorded.
final class FakeModelServer implements AutoCloseable {

    private static final String DEFAULT_ANSWER = "Done.";
    static final String SIDE_ANSWER = "Acme logons";

    private final HttpServer server;
    private final Deque<Map<String, Object>> replies = new ConcurrentLinkedDeque<>();
    private final List<Map<String, Object>> agentRequests = Collections.synchronizedList(new ArrayList<>());
    private final List<Map<String, Object>> sideRequests = Collections.synchronizedList(new ArrayList<>());

    private FakeModelServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/v1/chat/completions", this::handle);
        server.start();
    }

    /// @return A server, started.
    /// @throws IOException if it can't be started.
    static FakeModelServer start() throws IOException {
        return new FakeModelServer();
    }

    /// @return The base URL to give the model client.
    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    /// Adds an answer to the agent's replies, streamed a word at a time.
    ///
    /// @param text The answer.
    /// @return This server.
    FakeModelServer answer(final String text) {
        replies.add(Map.of("answer", text));
        return this;
    }

    /// Adds a call of a tool to the agent's replies.
    ///
    /// @param name          The tool.
    /// @param argumentsJson Its arguments, as JSON.
    /// @return This server.
    FakeModelServer callTool(final String name, final String argumentsJson) {
        replies.add(Map.of("tool", name, "arguments", argumentsJson));
        return this;
    }

    /// @return The body of every request that offered tools (the agent's turns), in order.
    List<Map<String, Object>> agentRequests() {
        synchronized (agentRequests) {
            return List.copyOf(agentRequests);
        }
    }

    /// @return The body of every request that offered no tools (e.g. naming the chat, or a batch of a table being
    /// summarised), in order.
    List<Map<String, Object>> sideRequests() {
        synchronized (sideRequests) {
            return List.copyOf(sideRequests);
        }
    }

    @SuppressWarnings("unchecked")
    private void handle(final HttpExchange exchange) throws IOException {
        try (exchange) {
            final String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            final Map<String, Object> request = JsonUtil.readValue(body, Map.class);
            final boolean agentTurn = request.get("tools") instanceof final List<?> tools && !tools.isEmpty();
            final Map<String, Object> reply;
            if (agentTurn) {
                agentRequests.add(request);
                reply = replies.isEmpty()
                        ? Map.of("answer", DEFAULT_ANSWER)
                        : replies.poll();
            } else {
                sideRequests.add(request);
                reply = Map.of("answer", SIDE_ANSWER);
            }
            final boolean streamed = !Boolean.FALSE.equals(request.get("stream"));
            if (streamed) {
                stream(exchange, reply);
            } else {
                whole(exchange, reply);
            }
        }
    }

    private void stream(final HttpExchange exchange, final Map<String, Object> reply) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, 0);
        final OutputStream out = exchange.getResponseBody();
        if (reply.containsKey("tool")) {
            send(out, chunk(Map.of("role", "assistant", "tool_calls", List.of(Map.of(
                    "index", 0,
                    "id", "call_" + agentRequests.size(),
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

    // A reply not streamed: the whole completion as one JSON object.
    private void whole(final HttpExchange exchange, final Map<String, Object> reply) throws IOException {
        final Map<String, Object> message = new HashMap<>();
        message.put("role", "assistant");
        final String finishReason;
        if (reply.containsKey("tool")) {
            message.put("tool_calls", List.of(Map.of(
                    "id", "call_" + agentRequests.size(),
                    "type", "function",
                    "function", Map.of("name", reply.get("tool"), "arguments", reply.get("arguments")))));
            finishReason = "tool_calls";
        } else {
            message.put("content", reply.get("answer"));
            finishReason = "stop";
        }
        final Map<String, Object> choice = new HashMap<>();
        choice.put("index", 0);
        choice.put("message", message);
        choice.put("finish_reason", finishReason);
        final Map<String, Object> completion = new HashMap<>();
        completion.put("id", "c1");
        completion.put("object", "chat.completion");
        completion.put("model", "fake");
        completion.put("choices", List.of(choice));
        completion.put("usage", Map.of("prompt_tokens", 10, "completion_tokens", 5, "total_tokens", 15));
        final byte[] bytes = JsonUtil.writeValueAsString(completion, false).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, bytes.length);
        exchange.getResponseBody().write(bytes);
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
