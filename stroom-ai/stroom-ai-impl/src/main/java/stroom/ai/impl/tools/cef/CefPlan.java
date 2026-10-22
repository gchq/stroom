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

package stroom.ai.impl.tools.cef;

import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.fielddoc.Descriptions;
import stroom.ai.impl.tools.fielddoc.FieldDocs;
import stroom.ai.impl.tools.generation.DumpMode;
import stroom.ai.impl.tools.generation.MappingValidationException;
import stroom.ai.impl.tools.generation.XsltStyle;
import stroom.ai.impl.tools.validation.XsltSafety;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/// CEF (ArcSight Common Event Format) output from Events: which Event value goes to which CEF key: the header
/// (vendor, product, version, class id, name, severity), the fields every event shares, and per kind of event (its
/// action element: Authenticate, View, Network...) the rest. It generates the XSLT ([CefXslt]) and the
/// documentation's tables ([CefDocs]). Plans are kept in documents (the CEF XSLT's description), so the JSON is the
/// Python model's: read with [#fromJson(Map)], written with [#toJson(DumpMode)]. Ported from the Stroom MCP
/// server's `CefPlan` model in `utils/cef.py`.
///
/// @param output     `kafka`: one kafka-records:1 record per Event, its value the CEF line (a StandardKafkaProducer
///                   sends it); `text`: one CEF line per Event (a TextWriter writes them).
/// @param topic      kafka: the topic ArcSight reads.
/// @param kafkaKey   `kafka_key`: an XPath on the Event for each record's key, if any.
/// @param customKeys `custom_keys`: whether keys outside ArcSight's CEF dictionary may be sent.
/// @param vendor     Device Vendor.
/// @param product    Device Product.
/// @param version    Device Version.
/// @param signature  Device Event Class ID.
/// @param name       Name.
/// @param severity   Severity (`3` unless given).
/// @param common     Fields every event gets.
/// @param events     Per kind of event, by its action element (Authenticate, View, Network, ...): the fields it adds.
/// @param notSent    `not_sent`: Event values the sample holds that no key takes, with why (`path`, `event_type`,
///                   `why`): documented as not sent.
/// @param imports    Shared XSLTs imported (xsl:import), e.g. those an AGENTS doc's Required imports name.
/// @param style      How the CEF XSLT is written, as an Events translation's is (naming, variables, layout).
@JsonInclude(JsonInclude.Include.ALWAYS)
@JsonPropertyOrder({"output", "topic", "kafka_key", "custom_keys", "vendor", "product", "version", "signature",
        "name", "severity", "common", "events", "not_sent", "imports", "style"})
public record CefPlan(@JsonProperty("output") String output,
                      @JsonProperty("topic") String topic,
                      @JsonProperty("kafka_key") String kafkaKey,
                      @JsonProperty("custom_keys") boolean customKeys,
                      @JsonProperty("vendor") CefValue vendor,
                      @JsonProperty("product") CefValue product,
                      @JsonProperty("version") CefValue version,
                      @JsonProperty("signature") CefValue signature,
                      @JsonProperty("name") CefValue name,
                      @JsonProperty("severity") CefValue severity,
                      @JsonProperty("common") List<CefField> common,
                      @JsonProperty("events") Map<String, List<CefField>> events,
                      @JsonProperty("not_sent") List<Map<String, String>> notSent,
                      @JsonProperty("imports") List<String> imports,
                      @JsonProperty("style") XsltStyle style) {

    /// One Kafka record per Event.
    public static final String KAFKA = "kafka";
    /// One line of text per Event.
    public static final String TEXT = "text";
    /// The outputs.
    public static final List<String> OUTPUTS = List.of(KAFKA, TEXT);
    /// The severity unless one is given.
    public static final CefValue DEFAULT_SEVERITY = CefValue.constant("3");

    private static final String NOT_A_SEVERITY = "is not a CEF severity (0 to 10, or Low, Medium, High, Very-High)";
    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9]*");

    /// Fills in the defaults for nulls.
    public CefPlan {
        output = Objects.requireNonNullElse(output, KAFKA);
        severity = Objects.requireNonNullElse(severity, DEFAULT_SEVERITY);
        common = List.copyOf(Objects.requireNonNullElse(common, List.of()));
        final Map<String, List<CefField>> kinds = new LinkedHashMap<>();
        Objects.requireNonNullElse(events, Map.<String, List<CefField>>of())
                .forEach((kind, fields) -> kinds.put(kind, List.copyOf(fields)));
        events = Collections.unmodifiableMap(kinds);
        notSent = List.copyOf(Objects.requireNonNullElse(notSent, List.of()));
        imports = List.copyOf(Objects.requireNonNullElse(imports, List.of()));
        style = Objects.requireNonNullElse(style, XsltStyle.DEFAULT);
    }

    /// @param output     `kafka` or `text`.
    /// @param topic      The Kafka topic, or null.
    /// @param customKeys Whether keys outside the dictionary may be sent.
    /// @param header     The header's fields by name (`vendor`, ...); a missing severity is `3`.
    /// @param common     Fields every event gets.
    /// @param events     Each kind of event's own fields.
    /// @return The plan, with no Kafka key, nothing not sent, no imports and the default style.
    public static CefPlan of(final String output,
                             final String topic,
                             final boolean customKeys,
                             final Map<String, CefValue> header,
                             final List<CefField> common,
                             final Map<String, List<CefField>> events) {
        return new CefPlan(output, topic, null, customKeys, header.get("vendor"), header.get("product"),
                header.get("version"), header.get("signature"), header.get("name"), header.get("severity"), common,
                events, List.of(), List.of(), null);
    }

    /// @param json A plan as JSON-like values (`model_dump()` of the Python model, or what an agent sends).
    /// @return It validated, as the Python model validates it.
    /// @throws MappingValidationException if it doesn't validate, with pydantic's errors.
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static CefPlan fromJson(final Map<String, Object> json) {
        final CefJsonReader reader = new CefJsonReader();
        final Map<String, Object> data = reader.model(json, List.of(), "CefPlan");
        if (data == null) {
            return reader.orThrow("CefPlan", null);
        }
        final List<Object> top = List.of();
        final String output = reader.literal(data, top, "output", KAFKA, OUTPUTS);
        final String topic = reader.optionalString(data, top, "topic");
        final String kafkaKey = reader.optionalString(data, top, "kafka_key");
        final boolean customKeys = reader.bool(data, top, "custom_keys", false);
        final Map<String, CefValue> header = new LinkedHashMap<>();
        for (final CefKeys.HeaderField field : CefKeys.HEADER) {
            final String key = field.name();
            if (!data.containsKey(key)) {
                if (!"severity".equals(key)) {
                    reader.error(List.of(key), "missing", "Field required", data);
                }
                continue;
            }
            header.put(key, CefValue.read(reader, data.get(key), List.of(key)));
        }
        final List<CefField> common = reader.list(data.get("common"), List.of("common"),
                (item, loc) -> CefField.read(reader, item, loc));
        final Map<String, List<CefField>> events = new LinkedHashMap<>();
        final Object kinds = data.get("events");
        if (kinds instanceof final Map<?, ?> map) {
            map.forEach((kind, fields) -> events.put(String.valueOf(kind), reader.list(fields,
                    List.of("events", String.valueOf(kind)), (item, loc) -> CefField.read(reader, item, loc))));
        } else if (kinds != null) {
            reader.error(List.of("events"), "dict_type", "Input should be a valid dictionary", kinds);
        }
        final List<Map<String, String>> notSent = reader.list(data.get("not_sent"), List.of("not_sent"),
                (item, loc) -> reader.stringMap(item, loc));
        final List<String> imports = reader.list(data.get("imports"), List.of("imports"), reader::string);
        XsltStyle style = XsltStyle.DEFAULT;
        if (data.get("style") instanceof final Map<?, ?> given) {
            try {
                @SuppressWarnings("unchecked") final Map<String, Object> styleJson = (Map<String, Object>) given;
                style = XsltStyle.fromJson(styleJson);
            } catch (final MappingValidationException e) {
                reader.addAll(List.of("style"), e.errors());
            }
        } else if (data.get("style") != null) {
            reader.error(List.of("style"), "model_type", "Input should be a valid dictionary or instance of "
                                                         + "XsltStyle", data.get("style"));
        }
        return reader.orThrow("CefPlan", new CefPlan(output, topic, kafkaKey, customKeys, header.get("vendor"),
                header.get("product"), header.get("version"), header.get("signature"), header.get("name"),
                header.get("severity"), common, events, notSent, imports, style));
    }

    /// @param name A header field's name (`vendor`, `product`, `version`, `signature`, `name`, `severity`).
    /// @return That field.
    public CefValue header(final String name) {
        return switch (name) {
            case "vendor" -> vendor;
            case "product" -> product;
            case "version" -> version;
            case "signature" -> signature;
            case "name" -> this.name;
            case "severity" -> severity;
            default -> throw new IllegalArgumentException("No header field " + name);
        };
    }

    /// @return The header's fields by name, in the header's order.
    public Map<String, CefValue> headers() {
        final Map<String, CefValue> out = new LinkedHashMap<>();
        CefKeys.HEADER.forEach(field -> out.put(field.name(), header(field.name())));
        return out;
    }

    /// @param name  A header field's name.
    /// @param value Its new value.
    /// @return The plan with that header field.
    public CefPlan withHeader(final String name, final CefValue value) {
        final Map<String, CefValue> header = headers();
        header.put(name, value);
        return new CefPlan(output, topic, kafkaKey, customKeys, header.get("vendor"), header.get("product"),
                header.get("version"), header.get("signature"), header.get("name"), header.get("severity"), common,
                events, notSent, imports, style);
    }

    /// @param output `kafka` or `text`.
    /// @return The plan with that output.
    public CefPlan withOutput(final String output) {
        return new CefPlan(output, topic, kafkaKey, customKeys, vendor, product, version, signature, name, severity,
                common, events, notSent, imports, style);
    }

    /// @param topic The Kafka topic, or null.
    /// @return The plan with that topic.
    public CefPlan withTopic(final String topic) {
        return new CefPlan(output, topic, kafkaKey, customKeys, vendor, product, version, signature, name, severity,
                common, events, notSent, imports, style);
    }

    /// @param kafkaKey An XPath for each Kafka record's key, or null.
    /// @return The plan with that key.
    public CefPlan withKafkaKey(final String kafkaKey) {
        return new CefPlan(output, topic, kafkaKey, customKeys, vendor, product, version, signature, name, severity,
                common, events, notSent, imports, style);
    }

    /// @param events Each kind of event's own fields.
    /// @return The plan with those.
    public CefPlan withEvents(final Map<String, List<CefField>> events) {
        return new CefPlan(output, topic, kafkaKey, customKeys, vendor, product, version, signature, name, severity,
                common, events, notSent, imports, style);
    }

    /// @param imports The shared XSLTs imported.
    /// @return The plan with those.
    public CefPlan withImports(final List<String> imports) {
        return new CefPlan(output, topic, kafkaKey, customKeys, vendor, product, version, signature, name, severity,
                common, events, notSent, imports, style);
    }

    /// @param style How the XSLT is written.
    /// @return The plan with that style.
    public CefPlan withStyle(final XsltStyle style) {
        return new CefPlan(output, topic, kafkaKey, customKeys, vendor, product, version, signature, name, severity,
                common, events, notSent, imports, style);
    }

    /// `expressions`: where and what each expression the XSLT evaluates is: header sources, fields and the Kafka
    /// key.
    ///
    /// @return (where, XPath) for each, e.g. `header Device Vendor` and its source.
    public List<Map.Entry<String, String>> expressions() {
        final List<Map.Entry<String, String>> found = new ArrayList<>();
        for (final CefKeys.HeaderField field : CefKeys.HEADER) {
            if (header(field.name()).source() != null) {
                found.add(Map.entry("header " + field.said(), header(field.name()).source()));
            }
        }
        common.forEach(f -> found.add(Map.entry("[common] " + f.key(), f.path())));
        events.forEach((kind, fields) -> fields.forEach(f -> found.add(Map.entry("[" + kind + "] " + f.key(),
                f.path()))));
        if (JsonValues.truthy(kafkaKey)) {
            found.add(Map.entry("kafka_key", kafkaKey));
        }
        return found;
    }

    /// `unsafe`: the expressions the tools won't run ([XsltSafety#refused(String, Map)]), each with why.
    ///
    /// @return One line each.
    public List<String> unsafe() {
        final List<String> out = new ArrayList<>();
        for (final Map.Entry<String, String> expression : expressions()) {
            final String refused = XsltSafety.refused(expression.getValue(), null);
            if (refused != null) {
                out.add(expression.getKey() + " " + expression.getValue() + ": " + refused);
            }
        }
        return out;
    }

    /// `problems`: what stops the plan being saved: unsafe expressions, a Kafka output with no topic, a header
    /// still to be asked for, severities CEF doesn't take, a key given twice, keys outside the dictionary the user
    /// hasn't allowed, labels given as fields, and custom slots with no label.
    ///
    /// @return One line each; none if the plan can be saved.
    public List<String> problems() {
        final List<String> out = new ArrayList<>(unsafe());
        if (KAFKA.equals(output) && !JsonValues.truthy(topic)) {
            out.add("output kafka needs topic: the Kafka topic ArcSight reads (ask the user, or the standing "
                    + "instructions may name it)");
        }
        for (final CefKeys.HeaderField field : CefKeys.HEADER) {
            final CefValue value = header(field.name());
            if (value.value() != null && value.value().startsWith("TODO")) {
                out.add("header " + field.said() + ": " + value.value());
            }
        }
        if (severity.value() != null && !CefKeys.SEVERITIES.contains(severity.value())) {
            out.add("severity '" + severity.value() + "' " + NOT_A_SEVERITY);
        }
        final List<String> mapped = new ArrayList<>(severity.map().values());
        if (JsonValues.truthy(severity.defaultValue())) {
            mapped.add(severity.defaultValue());
        }
        for (final String v : mapped) {
            if (!CefKeys.SEVERITIES.contains(v)) {
                out.add("severity map value '" + v + "' " + NOT_A_SEVERITY);
            }
        }
        final Map<String, List<CefField>> kinds = new LinkedHashMap<>();
        kinds.put("common", common);
        kinds.putAll(events);
        kinds.forEach((kind, fields) -> out.addAll(fieldProblems(kind, fields)));
        return out;
    }

    // One kind's fields' problems.
    private List<String> fieldProblems(final String kind, final List<CefField> fields) {
        final List<String> out = new ArrayList<>();
        final Map<String, String> seen = new LinkedHashMap<>();
        // A kind may give a common key: its own value is sent.
        for (final CefField f : fields) {
            if (seen.containsKey(f.key()) && !seen.get(f.key()).equals(f.path())) {
                out.add("[" + kind + "] " + f.key() + " is given twice (" + seen.get(f.key()) + " and " + f.path()
                        + "): one key, one value");
            }
            seen.putIfAbsent(f.key(), f.path());
        }
        for (final CefField f : fields) {
            if (!CefKeys.KEYS.containsKey(f.key())) {
                if (!customKeys) {
                    out.add("[" + kind + "] " + f.path() + ": '" + f.key() + "' is not a key in ArcSight's CEF "
                            + "dictionary, and the user has not allowed keys outside it: use a standard key or a "
                            + "custom slot (cs1-cs6, cn1-cn3, ...) with a label, or leave it out (not sent)");
                } else if (!KEY.matcher(f.key()).matches()) {
                    out.add("[" + kind + "] '" + f.key() + "': a CEF key is letters and digits");
                }
            } else if (f.key().endsWith("Label")) {
                out.add("[" + kind + "] " + f.key() + ": a label is given as the slot's label, not as a field of "
                        + "its own");
            } else if (CefKeys.SLOT_KEYS.contains(f.key()) && !JsonValues.truthy(f.label())) {
                out.add("[" + kind + "] " + f.path() + " -> " + f.key() + ": a custom slot needs a label saying what "
                        + "it holds");
            }
        }
        return out;
    }

    /// `xslt`: the CEF XSLT ([CefXslt#render(CefPlan)]).
    ///
    /// @return Its text.
    /// @throws IllegalArgumentException if an expression is unsafe: the XSLT isn't written.
    public String xslt() {
        return CefXslt.render(this);
    }

    /// `markdown`: the documentation's Field mapping section ([CefDocs#planMarkdown]).
    ///
    /// @param examples  Example values by (kind or `common` or `header`, key), from sampled lines.
    /// @param layout    [FieldDocs#BY_EVENT_TYPE] or [FieldDocs#COMPACT].
    /// @param described The field descriptions, or null.
    /// @return The section's text.
    public String markdown(final Map<CefDocs.KindKey, String> examples, final String layout,
                           final Descriptions described) {
        return CefDocs.planMarkdown(this, examples == null
                ? Map.of()
                : examples, layout == null
                ? FieldDocs.BY_EVENT_TYPE
                : layout, described);
    }

    /// @param mode How to write it: [DumpMode#ALL] is `model_dump()`, [DumpMode#COMPACT] is
    ///             `model_dump(exclude_defaults=True)` (how a plan is kept in a document).
    /// @return It as JSON, as the Python model writes it.
    public Map<String, Object> toJson(final DumpMode mode) {
        final Map<String, Object> out = new LinkedHashMap<>();
        CefValue.put(out, mode, "output", output, KAFKA);
        CefValue.put(out, mode, "topic", topic, null);
        CefValue.put(out, mode, "kafka_key", kafkaKey, null);
        CefValue.put(out, mode, "custom_keys", customKeys, false);
        for (final CefKeys.HeaderField field : CefKeys.HEADER) {
            final CefValue value = header(field.name());
            if (mode == DumpMode.COMPACT && "severity".equals(field.name()) && value.equals(DEFAULT_SEVERITY)) {
                continue;
            }
            out.put(field.name(), value.toJson(mode));
        }
        if (mode != DumpMode.COMPACT || !common.isEmpty()) {
            out.put("common", common.stream().map(f -> f.toJson(mode)).toList());
        }
        if (mode != DumpMode.COMPACT || !events.isEmpty()) {
            final Map<String, Object> kinds = new LinkedHashMap<>();
            events.forEach((kind, fields) -> kinds.put(kind, fields.stream().map(f -> f.toJson(mode)).toList()));
            out.put("events", kinds);
        }
        if (mode != DumpMode.COMPACT || !notSent.isEmpty()) {
            out.put("not_sent", notSent);
        }
        if (mode != DumpMode.COMPACT || !imports.isEmpty()) {
            out.put("imports", imports);
        }
        if (mode != DumpMode.COMPACT || !style.equals(XsltStyle.DEFAULT)) {
            out.put("style", style.toJson(mode));
        }
        return out;
    }
}
