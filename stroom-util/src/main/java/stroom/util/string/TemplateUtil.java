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

package stroom.util.string;

import stroom.util.io.FileUtil;
import stroom.util.io.HomeDirProvider;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

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

    public static final String UUID_VAR = "uuid";
    public static final String STROOM_TEMP_VAR = SimplePathCreator.STROOM_HOME;
    public static final String STROOM_HOME_VAR = SimplePathCreator.STROOM_TEMP;
    public static final Set<String> NON_ENV_VARS = Arrays.stream(SimplePathCreator.NON_ENV_VARS)
            .collect(Collectors.toSet());

    public static Template parseTemplate(final String template) {
        return parseTemplate(template, null, null);
    }

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
     * The template is case-sensitive.
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
            final List<PartExtractor> funcList = new ArrayList<>();
            final Set<String> varsInTemplate = new HashSet<>();
            final Map<String, PartExtractor> varToPartExtractorMap = new HashMap<>();
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
                        funcList.add(StaticPart.of(staticText));
                        LOGGER.debug("Adding static text func for '{}'", staticText);
                        sb.setLength(0);
                    }
                } else if (inVariable && chr == '}') {
                    inVariable = false;
                    // Intern it so if we are using static var replacement, we will benefit from
                    // only computing the hashcode once and String#equals being able to use '=='
                    final String var = sb.toString().intern();
                    varsInTemplate.add(var);
                    final PartExtractor partExtractor = varToPartExtractorMap.computeIfAbsent(var, aVar ->
                            new DynamicPart(aVar, effectiveParamFormatter));
                    funcList.add(partExtractor);
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
                funcList.add(StaticPart.of(staticText));
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
        private final Set<String> varsInTemplate;
        private final List<PartExtractor> partExtractors;
        private final int partExtractorCount;

        private Template(final String template,
                         final Set<String> varsInTemplate,
                         final List<PartExtractor> partExtractors) {
            this.template = Objects.requireNonNull(template);
            this.varsInTemplate = NullSafe.unmodifialbeSet(varsInTemplate);
            this.partExtractors = NullSafe.unmodifiableList(partExtractors);
            // Cache this
            this.partExtractorCount = this.partExtractors.size();
        }

        /**
         * Use the values in map to derive a string from the parsed template.
         *
         * @param varToReplacementMap A map of case-sensitive template variables (without their braces)
         *                            to the replacement value.
         * @see Template#buildExecutor() buildGenerator() for more control of variable replacement.
         */
        public String executeWith(final Map<String, String> varToReplacementMap) {
            // partExtractors cope with null map
            final String output;
            if (partExtractorCount == 0) {
                output = "";
            } else {
                final Map<String, String> map = NullSafe.map(varToReplacementMap);
                output = buildExecutor()
                        .addCommonReplacementFunction(map::get)
                        .execute();
            }

            LOGGER.debug("Generated output '{}' from varToReplacementProviderMap: {}",
                    output, varToReplacementMap);
            return output;
        }

        private String doExecute(final Map<String, ReplacementProvider> varToReplacementProviderMap,
                                 final List<OptionalReplacementProvider> dynamicReplacementProviders) {
            // partExtractors cope with null map
            final String output;
            if (partExtractorCount == 0) {
                output = "";
            } else if (partExtractorCount == 1) {
                return NullSafe.string(partExtractors.getFirst().apply(
                        varToReplacementProviderMap,
                        dynamicReplacementProviders));
            } else {
                final String[] parts = new String[partExtractorCount];
                for (int i = 0; i < partExtractorCount; i++) {
                    final PartExtractor partExtractor = partExtractors.get(i);
                    final String part = NullSafe.string(partExtractor.apply(
                            varToReplacementProviderMap,
                            dynamicReplacementProviders));
                    parts[i] = part;
                }
                return String.join("", parts);
            }

            LOGGER.debug("Generated output '{}' from varToReplacementProviderMap: {}, dynamicReplacementProviders: {}",
                    output, varToReplacementProviderMap, dynamicReplacementProviders);
            return output;
        }

        /**
         * Create a builder to add the replacements and generate the output.
         * {@link ExecutorBuilder} is not thread safe.
         */
        public ExecutorBuilder buildExecutor() {
            return new ExecutorBuilder(this);
        }

        /**
         * @return The set of vars in the template.
         */
        public Set<String> getVarsInTemplate() {
            return Collections.unmodifiableSet(varsInTemplate);
        }

        public boolean isVarInTemplate(final String var) {
            return varsInTemplate.contains(var);
        }

        @Override
        public String toString() {
            return template;
        }

        public boolean isEmpty() {
            return template.isEmpty();
        }

        public boolean isBlank() {
            return template.isBlank();
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
                   && Objects.equals(partExtractors, template.partExtractors);
        }

        @Override
        public int hashCode() {
            return Objects.hash(template, varsInTemplate, partExtractors);
        }
    }


    // --------------------------------------------------------------------------------


    public static class ExecutorBuilder {

        private static final SingleStatefulReplacementProvider STATEFUL_UUID_REPLACEMENT_PROVIDER =
                new SingleStatefulReplacementProvider(
                        UUID_VAR,
                        () -> UUID.randomUUID().toString());

        private static final ReplacementProvider STATELESS_UUID_REPLACEMENT_PROVIDER = ignored ->
                UUID.randomUUID().toString();

        private final Map<String, ReplacementProvider> varToReplacementProviderMap = new HashMap<>();
        /**
         * Replacement providers where the var is not known up front, e.g. replacing system properties.
         */
        private List<OptionalReplacementProvider> dynamicReplacementProviders = null;
        private final Template template;

        private ExecutorBuilder(final Template template) {
            this.template = template;
        }

        /**
         * Add a simple static replacement for var.
         * This will override any existing replacement for var.
         * If var is not in the template it is a no-op.
         */
        public ExecutorBuilder addReplacement(final String var, final String replacement) {
            if (NullSafe.isNonBlankString(var)) {
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

        /**
         * Add multiple static replacements. The map key is the var in the template and the
         * map value is the replacement.
         * This will override any existing replacements for vars matching the keys in replacementsMap.
         * Any entries where the var is not in the template will be ignored.
         */
        public ExecutorBuilder addReplacements(final Map<String, String> replacementsMap) {
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

        /**
         * Add a lazy static {@link ReplacementProvider} for var.
         * replacementSupplier will only be called once to get a replacement
         * even if var appears more than once in the template.
         * If var is not in the template it is a no-op.
         */
        public ExecutorBuilder addLazyReplacement(final String var,
                                                  final Supplier<String> replacementSupplier) {
            Objects.requireNonNull(replacementSupplier);
            if (NullSafe.isNonBlankString(var)) {
                if (template.isVarInTemplate(var)) {
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

        /**
         * Add a single {@link ReplacementProvider} function that will be used for <strong>ALL</strong>
         * vars in the template. It will override any other replacements that have been set.
         * <p>
         * Use this if you don't know what the vars will be in the template, e.g. the replacementProvider
         * will resolve them from some other source.
         * </p>
         */
        public ExecutorBuilder addCommonReplacementFunction(final ReplacementProvider replacementProvider) {
            Objects.requireNonNull(replacementProvider);

            final ReplacementProvider statefulReplacementProvider = new CommonStatefulReplacementProvider(
                    replacementProvider);
            for (final String var : template.getVarsInTemplate()) {
                varToReplacementProviderMap.put(var, statefulReplacementProvider);
            }
            return this;
        }

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
        public ExecutorBuilder addStandardTimeReplacements() {
            addStandardTimeReplacements(ZonedDateTime.now(ZoneOffset.UTC));
            return this;
        }

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
        public ExecutorBuilder addStandardTimeReplacements(final ZonedDateTime zonedDateTime) {
            final Set<String> varsInTemplate = template.getVarsInTemplate();

            addTimeReplacement("year", varsInTemplate, zonedDateTime::getYear, 4);
            addTimeReplacement("month", varsInTemplate, zonedDateTime::getMonthValue, 2);
            addTimeReplacement("day", varsInTemplate, zonedDateTime::getDayOfMonth, 2);
            addTimeReplacement("hour", varsInTemplate, zonedDateTime::getHour, 2);
            addTimeReplacement("minute", varsInTemplate, zonedDateTime::getMinute, 2);
            addTimeReplacement("second", varsInTemplate, zonedDateTime::getSecond, 2);
            addTimeReplacement("millis", varsInTemplate, () ->
                    zonedDateTime.getLong(ChronoField.MILLI_OF_SECOND), 3);
            addTimeReplacement("ms", varsInTemplate, () -> zonedDateTime.toInstant().toEpochMilli(), 3);
            return this;
        }

        /**
         * Add a replacement for {@code ${uuid}} with a randomly generated UUID.
         *
         * @param reuseUUidValue If true, a single randomly generated UUID will be used for all occurrences
         *                       of {@code ${uuid}}, else a random UUID will be generated for each.
         * @return
         */
        public ExecutorBuilder addUuidReplacement(final boolean reuseUUidValue) {
            if (reuseUUidValue) {
                varToReplacementProviderMap.put(UUID_VAR, STATEFUL_UUID_REPLACEMENT_PROVIDER);
            } else {
                varToReplacementProviderMap.put(UUID_VAR, STATELESS_UUID_REPLACEMENT_PROVIDER);
            }
            return this;
        }

        /**
         * Add the standard replacements for {@code ${stroom.home}} and {@code ${stroom.temp}}. It will
         * also try to resolve each var as a system property followed by an environment variable (except
         * for any in this set {@link TemplateUtil#NON_ENV_VARS}.
         *
         * @param homeDirProvider Provider of the stroom home dir.
         * @param tempDirProvider Provider of the stroom temp dir.
         */
        public ExecutorBuilder addSystemPropertyReplacements(final HomeDirProvider homeDirProvider,
                                                             final TempDirProvider tempDirProvider) {
            if (template.varsInTemplate.contains(STROOM_HOME_VAR)) {
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

            if (template.varsInTemplate.contains(STROOM_TEMP_VAR)) {
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

            dynamicReplacementProviders.add(var -> {
                if (!NON_ENV_VARS.contains(var)) {
                    return getSystemProperty(var)
                            .or(() -> getEnvVar(var));
                } else {
                    return Optional.empty();
                }
            });

            return this;
        }

        /**
         * Adds a dynamic replacement provider to the list of dynamic replacement providers that will be called
         * in turn if there is no static replacement provider for the var.
         */
        public ExecutorBuilder addDynamicReplacementProvider(final OptionalReplacementProvider replacementProvider) {
            if (replacementProvider != null) {
                if (dynamicReplacementProviders == null) {
                    dynamicReplacementProviders = new ArrayList<>();
                }
                dynamicReplacementProviders.add(replacementProvider);
            }
            return this;
        }

        /**
         * Sets the list of dynamic replacement providers that will be called
         * in turn if there is no static replacement provider for the var.
         */
        public ExecutorBuilder setDynamicReplacementProviders(
                final List<OptionalReplacementProvider> replacementProviders) {
            if (replacementProviders != null) {
                dynamicReplacementProviders = new ArrayList<>(NullSafe.list(replacementProviders));
            }
            return this;
        }

        /**
         * Execute the template using the provided replacements to output a string.
         *
         * @return The String generated from replacing the variables in the template.
         */
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

        private void addTimeReplacement(final String var,
                                        final Set<String> varsInTemplate,
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
    public interface ReplacementProvider extends Function<String, String> {

        /**
         * @param var The variable being replaced
         * @return The replacement value
         */
        @Override
        String apply(final String var);
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    public interface OptionalReplacementProvider extends Function<String, Optional<String>> {

        /**
         * @param var The variable being replaced
         * @return The replacement value
         */
        @Override
        Optional<String> apply(final String var);
    }


    // --------------------------------------------------------------------------------


    private static class SingleStatefulReplacementProvider implements ReplacementProvider {

        private final String var;
        private final Supplier<String> replacementSupplier;
        private String replacement = null;

        private SingleStatefulReplacementProvider(final String var,
                                                  final Supplier<String> replacementSupplier) {
            this.var = var;
            this.replacementSupplier = replacementSupplier;
        }

        @Override
        public String apply(final String var) {
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
        private final Map<String, String> replacements = new HashMap<>();

        private CommonStatefulReplacementProvider(final ReplacementProvider replacementProvider) {
            this.replacementProvider = replacementProvider;
        }

        @Override
        public String apply(final String var) {
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


    @FunctionalInterface
    private interface PartExtractor extends BiFunction<
            Map<String, ReplacementProvider>,
            List<OptionalReplacementProvider>,
            String> {

        @Override
        String apply(Map<String, ReplacementProvider> stringReplacementProviderMap,
                     List<OptionalReplacementProvider> dynamicReplacementProviders);
    }


    // --------------------------------------------------------------------------------


    private static class DynamicPart implements PartExtractor {

        private final String var;
        private final Function<String, String> formatter;

        private DynamicPart(final String var,
                            final Function<String, String> formatter) {
            this.var = var;
            this.formatter = formatter;
        }

        @Override
        public String apply(final Map<String, ReplacementProvider> varToReplacementProviderMap,
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


    private record StaticPart(String staticText) implements PartExtractor {

        private StaticPart(final String staticText) {
            this.staticText = NullSafe.string(staticText);
        }

        private static StaticPart of(final String staticText) {
            return new StaticPart(staticText);
        }

        @Override
        public String apply(final Map<String, ReplacementProvider> stringReplacementProviderMap,
                            final List<OptionalReplacementProvider> dynamicReplacementProviders) {
            return staticText;
        }
    }
}
