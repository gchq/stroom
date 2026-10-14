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

package stroom.ai.client;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONBoolean;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads the JSON held by TOOL_CALL and TOOL_RESULT chat messages, for display.
 */
final class AiToolMessages {

    private AiToolMessages() {
        // Static only.
    }

    /**
     * @param json The message's JSON.
     * @param name A field name.
     * @return The field's value as a string, or null if it is missing or the JSON cannot be read.
     */
    static String getString(final String json, final String name) {
        final JSONValue value = getValue(json, name);
        if (value == null) {
            return null;
        }
        final JSONString string = value.isString();
        return string != null
                ? string.stringValue()
                : value.toString();
    }

    /**
     * @param json The message's JSON.
     * @param name A field name.
     * @return The field's value if it is a boolean, otherwise false.
     */
    static boolean getBoolean(final String json, final String name) {
        final JSONValue value = getValue(json, name);
        final JSONBoolean bool = value != null
                ? value.isBoolean()
                : null;
        return bool != null && bool.booleanValue();
    }

    private static JSONValue getValue(final String json, final String name) {
        if (json == null || json.isEmpty()) {
            return null;
        }
        try {
            final JSONObject object = JSONParser.parseStrict(json).isObject();
            return object != null
                    ? object.get(name)
                    : null;
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * @param ms A duration in milliseconds.
     * @return The duration for people, e.g. "0.3s", "6s" or "1m 42s".
     */
    static String duration(final long ms) {
        if (ms < 1000) {
            return (Math.max(1, ms / 100) / 10.0) + "s";
        }
        final long seconds = Math.round(ms / 1000.0);
        if (seconds < 60) {
            return seconds + "s";
        }
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }

    /**
     * @param callJson A TOOL_CALL message's JSON.
     * @return A short heading saying what the call does, built from the tool and its key arguments.
     */
    static String toolHeading(final String callJson) {
        final String tool = getString(callJson, "tool");
        final String arguments = getString(callJson, "arguments");
        final String path = shorten(getString(arguments, "path"));
        if (tool == null) {
            return "Called a tool";
        }
        switch (tool) {
            case "ls":
                return "Listed " + (path != null
                        ? path
                        : "/");
            case "read_file":
                return "Read " + path;
            case "grep":
                return "Searched " + path + " for " + quoted(getString(arguments, "pattern"));
            case "wc":
                return "Counted " + path;
            case "write_file":
                return "Wrote " + path;
            case "str_replace":
                return "Edited " + path;
            case "delete_file":
                return "Deleted " + path;
            case "jq":
                return "Ran jq on " + path;
            case "xpath":
                return "Ran XPath on " + path;
            case "table":
                return "Ran a table query on " + path;
            case "pipeline":
                return "Ran a pipeline of tools";
            case "run_stroomql":
                return "Ran a StroomQL query";
            case "summarise_column":
                return "Summarised column " + quoted(getString(arguments, "column")) + " of " + path;
            case "memory":
                return "Memory: " + getString(arguments, "command") + (path != null
                        ? " " + path
                        : "");
            case "read_turn":
                return "Read turn " + getString(arguments, "turn") + " of the conversation";
            case "search_history":
                return "Searched the conversation for " + quoted(getString(arguments, "text"));
            default:
                // An MCP tool: its name, with the server prefix, plus its first argument.
                final String first = firstStringArgument(arguments);
                return "Called " + tool + (first != null
                        ? " " + quoted(first)
                        : "");
        }
    }

    /**
     * @param resultJson A TOOL_RESULT message's JSON.
     * @return How the call went, e.g. " · 2.1s" or " · failed · 0.3s".
     */
    static String resultSuffix(final String resultJson) {
        final String durationMs = getString(resultJson, "durationMs");
        final String took = durationMs != null
                ? " · " + duration(parseLong(durationMs))
                : "";
        return (getBoolean(resultJson, "error")
                ? " · failed"
                : "") + took;
    }

    /**
     * @param json A THINKING message: JSON with the text and how long it took, or plain text for older messages.
     * @return The reasoning text.
     */
    static String thinkingText(final String json) {
        final String text = json != null && json.startsWith("{")
                ? getString(json, "text")
                : null;
        return text != null
                ? text
                : json;
    }

    /**
     * @param json A THINKING message.
     * @return The reasoning to show under its heading: the text, less its first line if that is the heading.
     */
    static String thinkingBody(final String json) {
        final String text = thinkingText(json);
        if (text == null) {
            return "";
        }
        final String trimmed = text.trim();
        final String[] parts = trimmed.split("\n", 2);
        final String firstLine = parts[0].trim();
        final boolean headed = (firstLine.startsWith("**") && firstLine.endsWith("**") && firstLine.length() > 4)
                               || firstLine.startsWith("#");
        return headed && parts.length > 1
                ? parts[1].trim()
                : trimmed;
    }

    /**
     * @param json A THINKING message.
     * @return A heading for the reasoning: the model's own heading if it starts with one, otherwise how long it
     * thought for.
     */
    static String thinkingTitle(final String json) {
        final String text = thinkingText(json);
        final String firstLine = text != null
                ? text.trim().split("\n", 2)[0].trim()
                : "";
        if (firstLine.startsWith("**") && firstLine.endsWith("**") && firstLine.length() > 4) {
            return firstLine.substring(2, firstLine.length() - 2);
        }
        if (firstLine.startsWith("#")) {
            return firstLine.replaceFirst("^#+\\s*", "");
        }
        final String durationMs = json != null && json.startsWith("{")
                ? getString(json, "durationMs")
                : null;
        final long ms = durationMs != null
                ? parseLong(durationMs)
                : 0;
        return ms > 0
                ? "Thought for " + duration(ms)
                : "Thought";
    }

    private static String firstStringArgument(final String arguments) {
        try {
            final JSONObject object = arguments != null
                    ? JSONParser.parseStrict(arguments).isObject()
                    : null;
            if (object != null) {
                for (final String key : object.keySet()) {
                    final JSONString string = object.get(key).isString();
                    if (string != null) {
                        return shorten(string.stringValue());
                    }
                }
            }
        } catch (final RuntimeException e) {
            // Not JSON, so nothing to show.
        }
        return null;
    }

    private static String quoted(final String text) {
        if (text == null) {
            return "";
        }
        return "\u201c" + shorten(text) + "\u201d";
    }

    private static String shorten(final String text) {
        if (text == null) {
            return null;
        }
        final String oneLine = text.replace('\n', ' ');
        return oneLine.length() > 40
                ? oneLine.substring(0, 39) + "\u2026"
                : oneLine;
    }

    private static long parseLong(final String text) {
        try {
            return (long) Double.parseDouble(text);
        } catch (final RuntimeException e) {
            return 0;
        }
    }

    /**
     * @return Markdown showing a tool call's arguments.
     */
    static String callMarkdown(final String json) {
        final String arguments = getString(json, "arguments");
        return "Arguments:\n\n~~~json\n" + (arguments != null
                ? arguments
                : "{}") + "\n~~~";
    }

    /**
     * @return Markdown showing a tool call's result.
     */
    static String resultMarkdown(final String json) {
        final StringBuilder sb = new StringBuilder();
        final String path = getString(json, "path");
        if (path != null) {
            sb.append("The full result was saved to the workspace file `").append(path).append("`.\n\n");
        }
        sb.append("Result:\n\n~~~\n").append(getString(json, "text") != null
                ? getString(json, "text")
                : "").append("\n~~~");
        return sb.toString();
    }

    /**
     * @return A one-line description of a compaction of the chat.
     */
    static String compactionTitle(final String json) {
        final String count = getString(json, "messageCount");
        final String before = getString(json, "tokensBefore");
        final String after = getString(json, "tokensAfter");
        final String title = "Earlier messages compacted" + (count != null
                ? ": " + count + " messages"
                : "");
        return getBoolean(json, "stale")
                ? title + " (no longer used, as a message it covered has changed)"
                : before != null && after != null
                        ? title + ", about " + kilo(before) + " → " + kilo(after) + " tokens"
                        : title;
    }

    /**
     * @return Markdown showing what a compaction of the chat did.
     */
    static String compactionMarkdown(final String json) {
        final String summary = getString(json, "summary");
        final StringBuilder sb = new StringBuilder("The model now sees earlier turns as short digests");
        if (summary != null) {
            sb.append(", and the oldest as this summary:\n\n").append(summary);
        } else {
            sb.append(". Everything is still stored, and the model can read any earlier turn again.");
        }
        return sb.toString();
    }

    private static String kilo(final String tokens) {
        try {
            final long value = Long.parseLong(tokens);
            return value >= 1000
                    ? (value / 1000) + "k"
                    : String.valueOf(value);
        } catch (final NumberFormatException e) {
            return tokens;
        }
    }

    /**
     * @return Markdown showing a change to memory, as before and after.
     */
    static String memoryChangeMarkdown(final String json) {
        final String before = getString(json, "before");
        final String after = getString(json, "after");
        final StringBuilder sb = new StringBuilder();
        if (before != null) {
            sb.append(after != null
                    ? "Before:"
                    : "Deleted:").append("\n\n~~~\n").append(before).append("\n~~~\n\n");
        }
        if (after != null) {
            sb.append(before != null
                    ? "After:"
                    : "Created:").append("\n\n~~~\n").append(after).append("\n~~~\n");
        }
        return sb.toString();
    }

    /**
     * @return True if the request is for tool calls (rather than a change to memory).
     */
    static boolean isToolRequest(final String json) {
        return "TOOL".equals(getString(json, "kind"));
    }

    /**
     * @return Markdown describing a request to approve tool calls, with each call's arguments in full.
     */
    static String toolApprovalMarkdown(final String json) {
        final StringBuilder sb = new StringBuilder("**The agent wants to use tools that need your approval.**");
        final String reason = getString(json, "reason");
        if (reason != null) {
            sb.append(" ").append(reason);
        }
        final JSONArray calls = getArray(json, "calls");
        for (int i = 0; calls != null && i < calls.size(); i++) {
            final JSONObject call = calls.get(i).isObject();
            if (call == null) {
                continue;
            }
            sb.append("\n\n**`").append(text(call, "tool")).append("`**");
            final String server = text(call, "serverKey");
            if (server != null) {
                sb.append(" on ").append(server);
            }
            final JSONArray risks = call.get("risks") != null
                    ? call.get("risks").isArray()
                    : null;
            if (risks != null && risks.size() > 0) {
                sb.append(" (");
                for (int r = 0; r < risks.size(); r++) {
                    if (r > 0) {
                        sb.append(", ");
                    }
                    sb.append(risks.get(r).isString() != null
                            ? risks.get(r).isString().stringValue()
                            : "");
                }
                sb.append(")");
            }
            final String decision = text(call, "decision");
            if (decision != null) {
                sb.append(" - *").append(decisionText(decision, text(call, "note"))).append("*");
            }
            sb.append("\n\n~~~json\n").append(text(call, "arguments") != null
                    ? text(call, "arguments")
                    : "{}").append("\n~~~");
        }
        final String status = getString(json, "status");
        if ("EXPIRED".equals(status)) {
            sb.append("\n\n*Not answered in time, so nothing was run.*");
        }
        return sb.toString();
    }

    /**
     * @return True if the user may allow the request's tools for the rest of the chat.
     */
    static boolean allowsForChat(final String json) {
        return getBoolean(json, "allowForChat");
    }

    /**
     * @return True if the user may allow the server's read-only tools for the rest of the chat.
     */
    static boolean allowsServerReadOnly(final String json) {
        return getBoolean(json, "allowServerReadOnly");
    }

    private static JSONArray getArray(final String json, final String name) {
        final JSONValue value = getValue(json, name);
        return value != null
                ? value.isArray()
                : null;
    }

    private static String text(final JSONObject object, final String name) {
        final JSONValue value = object.get(name);
        if (value == null || value.isNull() != null) {
            return null;
        }
        return value.isString() != null
                ? value.isString().stringValue()
                : value.toString();
    }

    private static String decisionText(final String decision, final String note) {
        switch (decision) {
            case "ALLOWED":
                return "allowed";
            case "DENIED":
                return note != null
                        ? "denied: " + note
                        : "denied";
            default:
                return "not run";
        }
    }

    /**
     * @return Markdown describing a request for the user's approval.
     */
    static String approvalMarkdown(final String json) {
        if (isToolRequest(json)) {
            return toolApprovalMarkdown(json);
        }
        final String status = getString(json, "status");
        final StringBuilder sb = new StringBuilder("**The agent wants to change your memory** (`")
                .append(getString(json, "path"))
                .append("`), which is loaded into all your chats.");
        final String reason = getString(json, "reason");
        if (reason != null) {
            sb.append(" ").append(reason);
        }
        sb.append("\n\n").append(memoryChangeMarkdown(json));
        if (status != null && !"PENDING".equals(status)) {
            sb.append("\n").append(approvalStatusText(status));
        }
        return sb.toString();
    }

    /**
     * @return True if the request is still waiting for an answer.
     */
    static boolean isPendingApproval(final String json) {
        return "PENDING".equals(getString(json, "status"));
    }

    /**
     * @return The JSON of a request for approval, with its status changed.
     */
    static String withStatus(final String json, final String status) {
        try {
            final JSONObject object = JSONParser.parseStrict(json).isObject();
            if (object == null) {
                return json;
            }
            object.put("status", new JSONString(status));
            return object.toString();
        } catch (final RuntimeException e) {
            return json;
        }
    }

    private static String approvalStatusText(final String status) {
        switch (status) {
            case "APPROVED":
                return "*Approved.*";
            case "REJECTED":
                return "*Rejected.*";
            case "EXPIRED":
                return "*Not made: the request expired.*";
            case "FAILED":
                return "*Not made: the memory had changed since the request was made.*";
            default:
                return "*" + status + "*";
        }
    }

    /**
     * @param json An APPROVAL_REQUEST message for tool calls.
     * @return Its calls, in order.
     */
    static List<ApprovalCall> approvalCalls(final String json) {
        final List<ApprovalCall> list = new ArrayList<>();
        final JSONArray calls = getArray(json, "calls");
        for (int i = 0; calls != null && i < calls.size(); i++) {
            final JSONObject call = calls.get(i).isObject();
            if (call != null) {
                final List<String> risks = new ArrayList<>();
                final JSONArray riskArray = call.get("risks") != null
                        ? call.get("risks").isArray()
                        : null;
                for (int r = 0; riskArray != null && r < riskArray.size(); r++) {
                    if (riskArray.get(r).isString() != null) {
                        risks.add(riskArray.get(r).isString().stringValue());
                    }
                }
                list.add(new ApprovalCall(text(call, "callId"), text(call, "tool"), text(call, "serverKey"), risks,
                        text(call, "arguments"), text(call, "decision"), text(call, "note")));
            }
        }
        return list;
    }

    /**
     * @param decision A call's decision, e.g. "ALLOWED".
     * @param note     The user's reason, if they gave one.
     * @return The decision for people.
     */
    static String decisionLabel(final String decision, final String note) {
        return decisionText(decision, note);
    }

    /**
     * Records decisions in an APPROVAL_REQUEST message, as the server has, so the card can be redrawn without
     * asking the server for it again.
     *
     * @param json    The message's JSON.
     * @param callIds The calls decided, or null for all those not yet decided.
     * @param allowed True if they were allowed.
     * @param note    The user's reason for denying them, or null.
     * @return The updated JSON. Its status is ANSWERED once every call has been decided.
     */
    static String withDecisions(final String json,
                                final List<String> callIds,
                                final boolean allowed,
                                final String note) {
        try {
            final JSONObject object = JSONParser.parseStrict(json).isObject();
            final JSONArray calls = object.get("calls").isArray();
            boolean allDecided = true;
            for (int i = 0; i < calls.size(); i++) {
                final JSONObject call = calls.get(i).isObject();
                if (text(call, "decision") == null
                    && (callIds == null || callIds.contains(text(call, "callId")))) {
                    call.put("decision", new JSONString(allowed
                            ? "ALLOWED"
                            : "DENIED"));
                    if (!allowed && note != null) {
                        call.put("note", new JSONString(note));
                    }
                }
                if (text(call, "decision") == null) {
                    allDecided = false;
                }
            }
            if (allDecided) {
                object.put("status", new JSONString("ANSWERED"));
            }
            return object.toString();
        } catch (final RuntimeException e) {
            return json;
        }
    }

    /**
     * @return The JSON laid out over several lines, or the text as it is if it is not JSON.
     */
    static native String prettyJson(String json) /*-{
        try {
            return JSON.stringify(JSON.parse(json), null, 2);
        } catch (e) {
            return json;
        }
    }-*/;


    // --------------------------------------------------------------------------------


    /**
     * A tool call waiting for, or given, the user's approval.
     */
    static final class ApprovalCall {

        private final String callId;
        private final String tool;
        private final String server;
        private final List<String> risks;
        private final String arguments;
        private final String decision;
        private final String note;

        private ApprovalCall(final String callId,
                             final String tool,
                             final String server,
                             final List<String> risks,
                             final String arguments,
                             final String decision,
                             final String note) {
            this.callId = callId;
            this.tool = tool;
            this.server = server;
            this.risks = risks;
            this.arguments = arguments;
            this.decision = decision;
            this.note = note;
        }

        String getCallId() {
            return callId;
        }

        String getTool() {
            return tool;
        }

        String getServer() {
            return server;
        }

        List<String> getRisks() {
            return risks;
        }

        String getArguments() {
            return arguments;
        }

        String getDecision() {
            return decision;
        }

        String getNote() {
            return note;
        }
    }
}
