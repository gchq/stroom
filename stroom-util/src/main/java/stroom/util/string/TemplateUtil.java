/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.string;

import stroom.util.io.FileUtil;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;
import stroom.util.shared.string.CIKeys;

import com.google.common.base.Strings;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoField;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TemplateUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TemplateUtil.class);

    public static final CIKey UUID_VAR = CIKey.internStaticKey("uuid");
    public static final CIKey STROOM_TEMP_VAR = CIKey.internStaticKey(SimplePathCreator.STROOM_HOME);
    public static final CIKey STROOM_HOME_VAR = CIKey.internStaticKey(SimplePathCreator.STROOM_TEMP);
    /**
     * Represents epoch millis
     */
    public static final CIKey MS_VAR = CIKey.internStaticKey("ms");
    /**
     * Represents the millis part of the current second.
     */
    public static final CIKey MILLIS_VAR = CIKey.internStaticKey("millis");
    public static final CIKey SECOND_VAR = CIKey.internStaticKey("second");
    public static final CIKey MINUTE_VAR = CIKey.internStaticKey("minute");
    public static final CIKey HOUR_VAR = CIKey.internStaticKey("hour");
    public static final CIKey DAY_VAR = CIKey.internStaticKey("day");
    public static final CIKey MONTH_VAR = CIKey.internStaticKey("month");
    public static final CIKey YEAR_VAR = CIKey.internStaticKey("year");
    public static final CIKey FILE_NAME_VAR = CIKey.internStaticKey("fileName");
    public static final CIKey FILE_STEM_VAR = CIKey.internStaticKey("fileStem");
    public static final CIKey FILE_EXTENSION_VAR = CIKey.internStaticKey("fileExtension");

    public static final Set<CIKey> NON_ENV_VARS;

    static {
        NON_ENV_VARS = Arrays.stream(SimplePathCreator.NON_ENV_VARS)
                .map(CIKey::internStaticKey)
                .collect(Collectors.toSet());
    }

    /**
     * Parses a template like '${accountId}_${component}_static_text' and returns
     * a {@link Template} that can be used many times to build strings from
     * the parsed template. The {@link Template} is intended to be cached,
     * held as a static or on a singleton depending on the lifetime of the template string.
     * <p>
     * The variables in the template are case-insensitive.
     * </p>
     *
     * @param template The template string to parse.
     */
    public static Template parseTemplate(final String template) {
        return parseTemplate(template, null, null);
    }

    /**
     * Parses a template like '${accountId}_${component}_static_text' and returns
     * a {@link Template} that can be used many times to build strings from
     * the parsed template. The {@link Template} is intended to be cached,
     * held as a static or on a singleton depending on the lifetime of the template string.
     * <p>
     * The variables in the template are case-insensitive.
     * </p>
     *
     * @param template  The template string to parse.
     * @param formatter An optional formatter that will be called on all
     *                  static parts and all
     *                  replacements for parameterised parts of the template.
     *                  e.g. {@link String#toUpperCase()} and/or to replace unwanted chars.
     */
    public static Template parseTemplate(final String template,
                                         final Function<String, String> formatter) {
        return parseTemplate(template, formatter, formatter);
    }

    /**
     * Parses a template like '${accountId}_${component}_static_text' and returns
     * a {@link Template} that can be used many times to build strings from
     * the parsed template. The {@link Template} is intended to be cached,
     * held as a static or on a singleton depending on the lifetime of the template string.
     * <p>
     * The variables in the template are case-insensitive.
     * </p>
     *
     * @param template             The template string to parse.
     * @param replacementFormatter An optional formatter that will be called on all
     *                             replacements for parameterised parts of the template.
     *                             e.g. {@link String#toUpperCase()} and/or to replace unwanted chars.
     * @param staticTextFormatter  An optional formatter that will be called on all
     *                             static parts of the template
     *                             e.g. {@link String#toUpperCase()} and/or to replace unwanted chars.
     */
    public static Template parseTemplate(final String template,
                                         final Function<String, String> replacementFormatter,
                                         final Function<String, String> staticTextFormatter) {
        if (NullSafe.isEmptyString(template)) {
            return Template.EMPTY_TEMPLATE;
        } else {
            final List<TemplatePart> funcList = new ArrayList<>();
            final Set<CIKey> varsInTemplate = new HashSet<>();
            final Map<CIKey, TemplatePart> varToPartExtractorMap = new HashMap<>();
            final StringBuilder sb = new StringBuilder();
            char lastChar = 0;
            boolean inVariable = false;
            final Function<String, String> effectiveParamFormatter = replacementFormatter != null
                    ? str -> replacementFormatter.apply(NullSafe.string(str))
                    : NullSafe::string;

            // 'Compile' the template into a list of PartExtractor instances, with each one
            // representing either a chunk of static text or a named variable for replacement.
            for (final char chr : template.toCharArray()) {
                if (chr == '{' && lastChar == '$') {
                    inVariable = true;
                    if (!sb.isEmpty()) {
                        // Stuff before must be static text
                        final String staticText = format(sb.toString(), staticTextFormatter);
                        funcList.add(StaticTextPart.of(staticText));
                        LOGGER.debug("Adding static text func for '{}'", staticText);
                        sb.setLength(0);
                    }
                } else if (inVariable && chr == '}') {
                    inVariable = false;
                    // Because we may use the var to lookup system props or env vars, we MUST
                    // retain the original case inside the CIKey, so can't user CIKey.ofIgnoringCase()
                    final CIKey var = CIKey.of(sb.toString());
                    varsInTemplate.add(var);
                    final TemplatePart templatePart = varToPartExtractorMap.computeIfAbsent(var, aVar ->
                            new VariablePart(aVar, effectiveParamFormatter));
                    funcList.add(templatePart);
                    LOGGER.debug("Adding replacement func for var '{}'", var);
                    sb.setLength(0);
                } else if (chr != '$') {
                    // might be static text or the name of the key
                    sb.append(chr);
                }
                lastChar = chr;
            }
            if (inVariable) {
                throw new IllegalArgumentException(LogUtil.message(
                        "Unclosed variable in template '{}'", template));
            }

            // Pick up any trailing static text
            if (!sb.isEmpty()) {
                // Stuff before must be static text
                final String staticText = format(sb.toString(), staticTextFormatter);
                funcList.add(StaticTextPart.of(staticText));
                sb.setLength(0);
            }
            return new Template(template, varsInTemplate, funcList);
        }
    }

    private static String format(final String str,
                                 final Function<String, String> formatter) {
        String result = NullSafe.string(str);
        if (formatter != null) {
            result = formatter.apply(result);
        }
        return result;
    }

    private static String normaliseStaticText(final String name,
                                              final Function<String, String> formatter) {
        String result = NullSafe.string(name);
        if (formatter != null) {
            result = formatter.apply(result);
        }
        return result;
    }


    // --------------------------------------------------------------------------------


    /**
     * Thread safe 'compiled' form of a {@link String} template containing named variables of
     * the form:
     * <p>
     * {@code ${feed}_${type}}
     * </p>
     */
    public static class Template {

        public static final Template EMPTY_TEMPLATE = new Template(
                "",
                Collections.emptySet(),
                Collections.emptyList());

        /**
         * Here for debugging and toString
         */
        private final String template;
        private final Set<CIKey> varsInTemplate;
        private final List<TemplatePart> templateParts;
        private final int partExtractorCount;
        private boolean isAllStaticText;

        private Template(final String template,
                         final Set<CIKey> varsInTemplate,
                         final List<TemplatePart> templateParts) {
            this.template = Objects.requireNonNull(template);
            this.varsInTemplate = NullSafe.unmodifialbeSet(varsInTemplate);
            this.templateParts = NullSafe.unmodifiableList(templateParts);
            // Cache this
            this.partExtractorCount = this.templateParts.size();
            this.isAllStaticText = isAllStatic(templateParts);
        }

        private boolean isAllStatic(final List<TemplatePart> templateParts) {
            if (templateParts.isEmpty()) {
                // Empty template, but we should never get here as EMPTY should be used.
                return true;
            } else {
                return templateParts.stream()
                        .allMatch(templatePart ->
                                templatePart instanceof StaticTextPart);
            }
        }

        /**
         * Use the values in map to derive a string from the parsed template.
         *
         * @param varToReplacementMap A map of case-sensitive template variables (without their braces)
         *                            to the replacement value.
         * @see Template#buildExecutor() buildGenerator() for more control of variable replacement.
         */
        public String executeWith(final Map<CIKey, String> varToReplacementMap) {
            // partExtractors cope with null map
            final String output;
            if (partExtractorCount == 0) {
                output = "";
            } else if (isAllStaticText) {
                // All static so there will be only one extractor
                output = templateParts.getFirst().apply(Collections.emptyMap(), Collections.emptyList());
            } else {
                if (NullSafe.isEmptyMap(varToReplacementMap)) {
                    output = buildExecutor().execute();
                } else {
                    output = buildExecutor()
                            .addCommonReplacementFunction(varToReplacementMap::get)
                            .execute();
                }
            }

            LOGGER.debug("Generated output '{}' from varToReplacementProviderMap: {}",
                    output, varToReplacementMap);
            return NullSafe.string(output);
        }

        private String doExecute(final Map<CIKey, ReplacementProvider> varToReplacementProviderMap,
                                 final List<OptionalReplacementProvider> dynamicReplacementProviders) {
            // partExtractors cope with null map
            final String output;
            if (partExtractorCount == 0) {
                output = "";
            } else if (partExtractorCount == 1) {
                return NullSafe.string(templateParts.getFirst().apply(
                        varToReplacementProviderMap,
                        dynamicReplacementProviders));
            } else {
                final String[] parts = new String[partExtractorCount];
                for (int i = 0; i < partExtractorCount; i++) {
                    final TemplatePart templatePart = templateParts.get(i);
                    final String part = NullSafe.string(templatePart.apply(
                            varToReplacementProviderMap,
                            dynamicReplacementProviders));
                    parts[i] = part;
                }
                return String.join("", parts);
            }

            LOGGER.debug("Generated output '{}' from template: '{}', varToReplacementProviderMap: {}, " +
                         "dynamicReplacementProviders: {}",
                    output, template, varToReplacementProviderMap, dynamicReplacementProviders);
            return output;
        }

        /**
         * Create a builder to add the replacements and generate the output.
         * {@link ExecutorBuilderImpl} is not thread safe.
         */
        public ExecutorBuilder buildExecutor() {
            if (isAllStaticText) {
                return new AllStaticExecutorBuilderImpl(this);
            } else {
                return new ExecutorBuilderImpl(this);
            }
        }

        /**
         * @return The set of vars in the template.
         */
        public Set<CIKey> getVarsInTemplate() {
            return Collections.unmodifiableSet(varsInTemplate);
        }

        public boolean isVarInTemplate(final CIKey var) {
            return var != null
                   && varsInTemplate.contains(var);
        }

        @Override
        public String toString() {
            return template;
        }

        /**
         * @return True if the template is empty.
         */
        public boolean isEmpty() {
            return template.isEmpty();
        }

        /**
         * @return True if the template is empty or contains only whitespace.
         */
        public boolean isBlank() {
            return template.isBlank();
        }

        /**
         * @return True if all the template is static text, i.e. it has no vars in it.
         */
        public boolean isStatic() {
            return isAllStaticText;
        }

        @Override
        public boolean equals(final Object object) {
            if (this == object) {
                return true;
            }
            if (object == null || getClass() != object.getClass()) {
                return false;
            }
            final Template template = (Template) object;
            return Objects.equals(this.template, template.template)
                   && Objects.equals(varsInTemplate, template.varsInTemplate)
                   && Objects.equals(templateParts, template.templateParts);
        }

        @Override
        public int hashCode() {
            return Objects.hash(template, varsInTemplate, templateParts);
        }
    }


    // --------------------------------------------------------------------------------


    public interface ExecutorBuilder {

        /**
         * Add a simple static replacement for var.
         * This will override any existing replacement for var.
         * If var is not in the template it is a no-op.
         */
        ExecutorBuilder addReplacement(CIKey var, String replacement);

        /**
         * Add multiple static replacements. The map key is the var in the template and the
         * map value is the replacement.
         * This will override any existing replacements for vars matching the keys in replacementsMap.
         * Any entries where the var is not in the template will be ignored.
         */
        ExecutorBuilder addReplacements(Map<CIKey, String> replacementsMap);

        /**
         * Add a lazy static {@link ReplacementProvider} for var.
         * replacementSupplier will only be called once to get a replacement
         * even if var appears more than once in the template.
         * If var is not in the template it is a no-op.
         */
        ExecutorBuilder addLazyReplacement(CIKey var,
                                           Supplier<String> replacementSupplier);

        /**
         * Add a single {@link ReplacementProvider} function that will be used for <strong>ALL</strong>
         * vars in the template. It will override any other replacements that have been set.
         * <p>
         * Use this if you don't know what the vars will be in the template, e.g. the replacementProvider
         * will resolve them from some other source.
         * </p>
         */
        ExecutorBuilder addCommonReplacementFunction(ReplacementProvider replacementProvider);

        /**
         * Add the following var replacements:
         * <ol>
         *     <li>{@code ${year}} => 4 digit year</li>
         *     <li>{@code ${month}} => 2 digit month</li>
         *     <li>{@code ${day}} => 2 digit day of month</li>
         *     <li>{@code ${hour}} => 2 digit hour of day</li>
         *     <li>{@code ${minute}} => 2 digit minute of hour</li>
         *     <li>{@code ${second}} => 2 digit second of hour</li>
         *     <li>{@code ${millis}} => 3 digit milliseconds of second</li>
         *     <li>{@code ${ms}} =>  milliseconds seconds since the unix epoch, not padded</li>
         * </ol>
         * <p>
         * Uses the current time in {@link ZoneOffset#UTC} for all the replacements.
         * </p>
         */
        ExecutorBuilder addStandardTimeReplacements();

        /**
         * Add the following var replacements:
         * <ol>
         *     <li>{@code ${year}} => 4 digit year</li>
         *     <li>{@code ${month}} => 2 digit month</li>
         *     <li>{@code ${day}} => 2 digit day of month</li>
         *     <li>{@code ${hour}} => 2 digit hour of day</li>
         *     <li>{@code ${minute}} => 2 digit minute of hour</li>
         *     <li>{@code ${second}} => 2 digit second of hour</li>
         *     <li>{@code ${millis}} => 3 digit milliseconds of second</li>
         *     <li>{@code ${ms}} =>  milliseconds seconds since the unix epoch, not padded</li>
         * </ol>
         *
         * @param zonedDateTime The time to use for all replacements.
         */
        ExecutorBuilder addStandardTimeReplacements(ZonedDateTime zonedDateTime);

        /**
         * Add a replacement for {@code ${uuid}} with a randomly generated UUID.
         *
         * @param reuseUUidValue If true, a single randomly generated UUID will be used for all occurrences
         *                       of {@code ${uuid}}, else a random UUID will be generated for each.
         */
        ExecutorBuilder addUuidReplacement(boolean reuseUUidValue);

        /**
         * Add the standard replacements for {@code ${stroom.home}} and {@code ${stroom.temp}}.
         * <p>
         * It will also try to resolve each var as a system property followed by an environment variable (except
         * for any in this set {@link TemplateUtil#NON_ENV_VARS}).
         * </p>
         * <p>
         * These replacements will be done if no other explicit replacements have been configured
         * for the variable, e.g. if the template is {@code "${FEED}"} and an explicit replacement for
         * {@code "FEED"} has been added, it will use that, else it will try to find a system property that
         * matches, else it will try to find an env var that matches.
         * </p>
         *
         * @param homeDirProvider Provider of the stroom home dir.
         * @param tempDirProvider Provider of the stroom temp dir.
         */
        ExecutorBuilder addSystemPropertyReplacements(HomeDirProvider homeDirProvider,
                                                      TempDirProvider tempDirProvider);

        /**
         * Adds these replacements using the supplied fileName value.
         * <ul>
         *     <li>{@code fileName} => fileName</li>
         *     <li>{@code fileStem} => Everything up to the last '.' in filename, e.g. 'foo' in 'foo.txt'</li>
         *     <li>{@code fileExtension} => The extension of fileName if present, e.g. 'txt' in 'foo.txt'</li>
         * </ul>
         *
         * @param fileName The file name to use in any replacements.
         */
        ExecutorBuilder addFileNameReplacement(String fileName);

        /**
         * Adds a dynamic replacement provider to the list of dynamic replacement providers that will be called
         * in turn if there is no static replacement provider for the var.
         */
        ExecutorBuilder addDynamicReplacementProvider(OptionalReplacementProvider replacementProvider);

        /**
         * Sets the list of dynamic replacement providers that will be called
         * in turn if there is no static replacement provider for the var.
         */
        ExecutorBuilder setDynamicReplacementProviders(
                List<OptionalReplacementProvider> replacementProviders);

        /**
         * Execute the template using the provided replacements to output a string.
         *
         * @return The String generated from replacing the variables in the template.
         */
        String execute();
    }


    // --------------------------------------------------------------------------------


    /**
     * Builder for a {@link Template} that is all static text, so replacement methods are all a no-op.
     */
    public static class AllStaticExecutorBuilderImpl implements ExecutorBuilder {

        private final Template template;

        private AllStaticExecutorBuilderImpl(final Template template) {
            this.template = template;
        }

        @Override
        public ExecutorBuilder addReplacement(final CIKey var, final String replacement) {
            // Template is all static text so this is a no-op
            return this;
        }

        @Override
        public ExecutorBuilder addReplacements(final Map<CIKey, String> replacementsMap) {
            // Template is all static text so this is a no-op
            return this;
        }

        @Override
        public ExecutorBuilder addLazyReplacement(final CIKey var, final Supplier<String> replacementSupplier) {
            // Template is all static text so this is a no-op
            return this;
        }

        @Override
        public ExecutorBuilder addCommonReplacementFunction(final ReplacementProvider replacementProvider) {
            // Template is all static text so this is a no-op
            return this;
        }

        @Override
        public ExecutorBuilder addStandardTimeReplacements() {
            // Template is all static text so this is a no-op
            return this;
        }

        @Override
        public ExecutorBuilder addStandardTimeReplacements(final ZonedDateTime zonedDateTime) {
            // Template is all static text so this is a no-op
            return this;
        }

        @Override
        public ExecutorBuilder addUuidReplacement(final boolean reuseUUidValue) {
            // Template is all static text so this is a no-op
            return this;
        }

        @Override
        public ExecutorBuilder addSystemPropertyReplacements(final HomeDirProvider homeDirProvider,
                                                             final TempDirProvider tempDirProvider) {
            // Template is all static text so this is a no-op
            return this;
        }

        @Override
        public ExecutorBuilder addFileNameReplacement(final String fileName) {
            // Template is all static text so this is a no-op
            return null;
        }

        @Override
        public ExecutorBuilder addDynamicReplacementProvider(final OptionalReplacementProvider replacementProvider) {
            // Template is all static text so this is a no-op
            return this;
        }

        @Override
        public ExecutorBuilder setDynamicReplacementProviders(
                final List<OptionalReplacementProvider> replacementProviders) {
            // Template is all static text so this is a no-op
            return this;
        }

        @Override
        public String execute() {
            // Just return the original template string as there is nothing to replace
            return template.template;
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Standard builder for executing a template with at least one variable in it.
     */
    public static class ExecutorBuilderImpl implements ExecutorBuilder {

        private static final SingleStatefulReplacementProvider STATEFUL_UUID_REPLACEMENT_PROVIDER =
                new SingleStatefulReplacementProvider(
                        UUID_VAR,
                        () -> UUID.randomUUID().toString());

        private static final ReplacementProvider STATELESS_UUID_REPLACEMENT_PROVIDER = ignored ->
                UUID.randomUUID().toString();

        private final Map<CIKey, ReplacementProvider> varToReplacementProviderMap = new HashMap<>();
        /**
         * Replacement providers where the var is not known up front, e.g. replacing system properties.
         */
        private List<OptionalReplacementProvider> dynamicReplacementProviders = null;
        private final Template template;

        private ExecutorBuilderImpl(final Template template) {
            this.template = template;
        }

        @Override
        public ExecutorBuilder addReplacement(final CIKey var, final String replacement) {
            Objects.requireNonNull(var);
            if (!var.isEmpty()) {
                if (template.isVarInTemplate(var)) {
                    if (NullSafe.isNonEmptyString(replacement)) {
                        // No point adding a func for an empty replacement
                        varToReplacementProviderMap.put(var, aVar -> replacement);
                    }
                } else {
                    LOGGER.debug("var '{}' is not in template '{}'", var, template.template);
                }
            } else {
                throw new IllegalArgumentException("Blank var");
            }
            return this;
        }

        @Override
        public ExecutorBuilder addReplacements(final Map<CIKey, String> replacementsMap) {
            NullSafe.map(replacementsMap)
                    .forEach((var, replacement) -> {
                        if (template.isVarInTemplate(var)
                            && NullSafe.isNonEmptyString(replacement)) {
                            // No point adding a func for an empty replacement
                            varToReplacementProviderMap.put(var, aVar -> replacement);
                        }
                    });
            return this;
        }

        @Override
        public ExecutorBuilder addLazyReplacement(final CIKey var,
                                                  final Supplier<String> replacementSupplier) {
            Objects.requireNonNull(var);
            if (!var.isEmpty()) {
                if (template.isVarInTemplate(var)) {
                    Objects.requireNonNull(replacementSupplier);
                    final SingleStatefulReplacementProvider singleStatefulReplacementProvider =
                            new SingleStatefulReplacementProvider(var, replacementSupplier);
                    varToReplacementProviderMap.put(var, singleStatefulReplacementProvider);
                } else {
                    LOGGER.debug("var '{}' is not in template '{}'", var, template.template);
                }
            } else {
                throw new IllegalArgumentException("Blank var");
            }
            return this;
        }

        @Override
        public ExecutorBuilder addCommonReplacementFunction(final ReplacementProvider replacementProvider) {
            Objects.requireNonNull(replacementProvider);

            final ReplacementProvider statefulReplacementProvider = new CommonStatefulReplacementProvider(
                    replacementProvider);
            for (final CIKey var : template.getVarsInTemplate()) {
                varToReplacementProviderMap.put(var, statefulReplacementProvider);
            }
            return this;
        }

        @Override
        public ExecutorBuilder addStandardTimeReplacements() {
            addStandardTimeReplacements(ZonedDateTime.now(ZoneOffset.UTC));
            return this;
        }

        @Override
        public ExecutorBuilder addStandardTimeReplacements(final ZonedDateTime zonedDateTime) {
            final Set<CIKey> varsInTemplate = template.getVarsInTemplate();

            addTimeReplacement(YEAR_VAR, varsInTemplate, zonedDateTime::getYear, 4);
            addTimeReplacement(MONTH_VAR, varsInTemplate, zonedDateTime::getMonthValue, 2);
            addTimeReplacement(DAY_VAR, varsInTemplate, zonedDateTime::getDayOfMonth, 2);
            addTimeReplacement(HOUR_VAR, varsInTemplate, zonedDateTime::getHour, 2);
            addTimeReplacement(MINUTE_VAR, varsInTemplate, zonedDateTime::getMinute, 2);
            addTimeReplacement(SECOND_VAR, varsInTemplate, zonedDateTime::getSecond, 2);
            addTimeReplacement(MILLIS_VAR, varsInTemplate, () ->
                    zonedDateTime.getLong(ChronoField.MILLI_OF_SECOND), 3);
            addTimeReplacement(MS_VAR, varsInTemplate, () -> zonedDateTime.toInstant().toEpochMilli(), 3);
            return this;
        }

        @Override
        public ExecutorBuilder addUuidReplacement(final boolean reuseUUidValue) {
            if (reuseUUidValue) {
                varToReplacementProviderMap.put(CIKeys.UUID, STATEFUL_UUID_REPLACEMENT_PROVIDER);
            } else {
                varToReplacementProviderMap.put(CIKeys.UUID, STATELESS_UUID_REPLACEMENT_PROVIDER);
            }
            return this;
        }

        @Override
        public ExecutorBuilder addSystemPropertyReplacements(final HomeDirProvider homeDirProvider,
                                                             final TempDirProvider tempDirProvider) {
            if (template.isVarInTemplate(STROOM_HOME_VAR)) {
                varToReplacementProviderMap.put(STROOM_HOME_VAR, new SingleStatefulReplacementProvider(
                        STROOM_HOME_VAR,
                        () -> {
                            if (homeDirProvider != null) {
                                return FileUtil.getCanonicalPath(homeDirProvider.get());
                            } else {
                                return "";
                            }
                        }));
            }

            if (template.isVarInTemplate(STROOM_TEMP_VAR)) {
                varToReplacementProviderMap.put(STROOM_TEMP_VAR, new SingleStatefulReplacementProvider(
                        STROOM_TEMP_VAR,
                        () -> {
                            if (tempDirProvider != null) {
                                return FileUtil.getCanonicalPath(tempDirProvider.get());
                            } else {
                                return "";
                            }
                        }));
            }

            if (dynamicReplacementProviders == null) {
                dynamicReplacementProviders = new ArrayList<>();
            }

            dynamicReplacementProviders.add(var -> {
                if (!NON_ENV_VARS.contains(var)) {
                    final String varStr = var.get();
                    return getSystemProperty(varStr)
                            .or(() -> getEnvVar(varStr));
                } else {
                    return Optional.empty();
                }
            });

            return this;
        }

        @Override
        public ExecutorBuilder addFileNameReplacement(final String fileName) {
            if (template.isVarInTemplate(FILE_NAME_VAR)) {
                varToReplacementProviderMap.put(FILE_NAME_VAR, new SingleStatefulReplacementProvider(
                        FILE_NAME_VAR, () -> NullSafe.string(fileName)));
            }

            if (template.isVarInTemplate(FILE_STEM_VAR)) {
                varToReplacementProviderMap.put(FILE_STEM_VAR, new SingleStatefulReplacementProvider(
                        FILE_STEM_VAR, () -> {
                    final String safeFileName = NullSafe.string(fileName);
                    String fileStem = safeFileName;
                    final int index = safeFileName.lastIndexOf(".");
                    if (index != -1) {
                        fileStem = safeFileName.substring(0, index);
                    }
                    return fileStem;
                }));
            }

            if (template.isVarInTemplate(FILE_EXTENSION_VAR)) {
                varToReplacementProviderMap.put(FILE_EXTENSION_VAR, new SingleStatefulReplacementProvider(
                        FILE_EXTENSION_VAR, () -> {
                    final String safeFileName = NullSafe.string(fileName);
                    String fileExtension = "";
                    final int index = safeFileName.lastIndexOf(".");
                    if (index != -1) {
                        fileExtension = safeFileName.substring(index + 1);
                    }
                    return fileExtension;
                }));
            }
            return this;
        }

        @Override
        public ExecutorBuilder addDynamicReplacementProvider(final OptionalReplacementProvider replacementProvider) {
            if (replacementProvider != null) {
                if (dynamicReplacementProviders == null) {
                    dynamicReplacementProviders = new ArrayList<>();
                }
                dynamicReplacementProviders.add(replacementProvider);
            }
            return this;
        }

        @Override
        public ExecutorBuilder setDynamicReplacementProviders(
                final List<OptionalReplacementProvider> replacementProviders) {
            if (replacementProviders != null) {
                dynamicReplacementProviders = new ArrayList<>(NullSafe.list(replacementProviders));
            }
            return this;
        }

        @Override
        public String execute() {
            return template.doExecute(
                    varToReplacementProviderMap,
                    NullSafe.list(dynamicReplacementProviders));
        }

        private Optional<String> getSystemProperty(final String key) {
            return Optional.ofNullable(System.getProperty(key));
        }

        private Optional<String> getEnvVar(final String key) {
            return Optional.ofNullable(System.getenv(key));
        }

        private void addTimeReplacement(final CIKey var,
                                        final Set<CIKey> varsInTemplate,
                                        final LongSupplier valueSupplier,
                                        final int pad) {
            if (varsInTemplate.contains(var)) {
                final ReplacementProvider stringReplacementSupplier = ignored -> {
                    String value = String.valueOf(valueSupplier.getAsLong());
                    if (pad > 0) {
                        value = Strings.padStart(value, pad, '0');
                    }
                    return value;
                };
                varToReplacementProviderMap.put(var, stringReplacementSupplier);
            }
        }
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    public interface ReplacementProvider extends Function<CIKey, String> {

        /**
         * @param var The variable being replaced
         * @return The replacement value
         */
        @Override
        String apply(final CIKey var);
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    public interface OptionalReplacementProvider extends Function<CIKey, Optional<String>> {

        /**
         * @param var The variable being replaced
         * @return The replacement value
         */
        @Override
        Optional<String> apply(final CIKey var);
    }


    // --------------------------------------------------------------------------------


    private static class SingleStatefulReplacementProvider implements ReplacementProvider {

        private final CIKey var;
        private final Supplier<String> replacementSupplier;
        private String replacement = null;

        private SingleStatefulReplacementProvider(final CIKey var,
                                                  final Supplier<String> replacementSupplier) {
            this.var = var;
            this.replacementSupplier = replacementSupplier;
        }

        @Override
        public String apply(final CIKey var) {
            if (!Objects.equals(var, this.var)) {
                throw new IllegalArgumentException(LogUtil.message("Vars are different! '{}' vs '{}'",
                        var, this.var));
            }
            if (replacement == null) {
                replacement = replacementSupplier.get();
            }
            return replacement;
        }
    }


    // --------------------------------------------------------------------------------


    private static class CommonStatefulReplacementProvider implements ReplacementProvider {

        private final ReplacementProvider replacementProvider;
        private final Map<CIKey, String> replacements = new HashMap<>();

        private CommonStatefulReplacementProvider(final ReplacementProvider replacementProvider) {
            this.replacementProvider = replacementProvider;
        }

        @Override
        public String apply(final CIKey var) {
            Objects.requireNonNull(var);
            String replacement = replacements.get(var);
            if (replacement == null) {
                replacement = replacementProvider.apply(var);
                replacements.put(var, replacement);
            }
            return replacement;
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * A logical part of the template, either a chunk of static text or a variable (i.e. {@code ${foo}})
     */
    @FunctionalInterface
    private interface TemplatePart extends BiFunction<
            Map<CIKey, ReplacementProvider>,
            List<OptionalReplacementProvider>,
            String> {

        @Override
        String apply(Map<CIKey, ReplacementProvider> stringReplacementProviderMap,
                     List<OptionalReplacementProvider> dynamicReplacementProviders);
    }


    // --------------------------------------------------------------------------------


    private static class VariablePart implements TemplatePart {

        private final CIKey var;
        private final Function<String, String> formatter;

        private VariablePart(final CIKey var,
                             final Function<String, String> formatter) {
            this.var = var;
            this.formatter = formatter;
        }

        @Override
        public String apply(final Map<CIKey, ReplacementProvider> varToReplacementProviderMap,
                            final List<OptionalReplacementProvider> dynamicReplacementProviders) {
            // Reuse the replacement across calls
            final ReplacementProvider replacementProvider = varToReplacementProviderMap.get(var);
            String output = null;
            if (replacementProvider != null) {
                output = replacementProvider.apply(var);
            } else {
                for (final OptionalReplacementProvider dynamicReplacementProvider : dynamicReplacementProviders) {
                    final Optional<String> optStr = dynamicReplacementProvider.apply(var);
                    if (optStr.isPresent()) {
                        output = optStr.get();
                        break;
                    }
                }
            }
            // Get the replacement then format it
            return NullSafe.getOrElse(output, formatter, "");
        }

        @Override
        public String toString() {
            return "DynamicPart{" +
                   "var='" + var + '\'' +
                   '}';
        }
    }


    // --------------------------------------------------------------------------------


    private record StaticTextPart(String staticText) implements TemplatePart {

        private StaticTextPart(final String staticText) {
            this.staticText = NullSafe.string(staticText);
        }

        private static StaticTextPart of(final String staticText) {
            Objects.requireNonNull(staticText);
            return new StaticTextPart(staticText);
        }

        @Override
        public String apply(final Map<CIKey, ReplacementProvider> stringReplacementProviderMap,
                            final List<OptionalReplacementProvider> dynamicReplacementProviders) {
            return staticText;
        }
    }
}
