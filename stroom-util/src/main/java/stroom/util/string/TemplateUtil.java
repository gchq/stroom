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

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class TemplateUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TemplateUtil.class);

    public static Templator parseTemplate(final String template) {
        return parseTemplate(template, null, null);
    }

    public static Templator parseTemplate(final String template,
                                          final Function<String, String> formatter) {
        return parseTemplate(template, formatter, formatter);
    }

    /**
     * Parses a template like '${accountId}_${component}_static_text' and returns
     * a {@link Templator} that can be used many times to build strings from
     * the parsed template.
     * <p>
     * The case sensitivity will depend on the implementation of the map
     * passed to {@link Templator#parseTemplate(String)}.
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
    public static Templator parseTemplate(final String template,
                                          final Function<String, String> replacementFormatter,
                                          final Function<String, String> staticTextFormatter) {
        if (NullSafe.isEmptyString(template)) {
            return Templator.EMPTY_TEMPLATE;
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
                    final String var = sb.toString();
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
            return new Templator(template, varsInTemplate, funcList);
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


    public static class Templator {

        private static final Templator EMPTY_TEMPLATE = new Templator(
                "",
                Collections.emptySet(),
                Collections.emptyList());

        /**
         * Here for debugging and toString
         */
        private final String template;
        private final Set<String> varsInTemplate;
        private final List<PartExtractor> partExtractors;

        private Templator(final String template,
                          final Set<String> varsInTemplate,
                          final List<PartExtractor> partExtractors) {
            this.template = template;
            this.varsInTemplate = NullSafe.unmodifialbeSet(varsInTemplate);
            this.partExtractors = NullSafe.unmodifiableList(partExtractors);
        }

        /**
         * Use the values in map to derive a string from the parsed template.
         *
         * @param varToReplacementMap A map of case-sensitive template variables (without their braces)
         *                            to the replacement value.
         */
        public String generateWith(final Map<String, String> varToReplacementMap) {
            // partExtractors cope with null map
            final String output;
            if (partExtractors.isEmpty()) {
                output = "";
            } else {
                final Map<String, String> map = NullSafe.map(varToReplacementMap);
                output = buildGenerator()
                        .addCommonReplacementFunction(map::get)
                        .generate();
            }

            LOGGER.debug("Generated output '{}' from varToReplacementProviderMap: {}",
                    output, varToReplacementMap);
            return output;
        }

        private String doGenerate(final Map<String, ReplacementProvider> varToReplacementMap) {
            // partExtractors cope with null map
            final String output;
            if (partExtractors.isEmpty()) {
                output = "";
            } else {
                output = partExtractors.stream()
                        .map(partExtractor ->
                                partExtractor.apply(varToReplacementMap))
                        .map(NullSafe::string)
                        .collect(Collectors.joining());
            }

            LOGGER.debug("Generated output '{}' from varToReplacementProviderMap: {}", output, varToReplacementMap);
            return output;
        }

        /**
         * Create a builder to add the replacements and generate the output.
         */
        public GeneratorBuilder buildGenerator() {
            return new GeneratorBuilder(this);
        }

        /**
         * @return The set of vars in the template.
         */
        public Set<String> getVarsInTemplate() {
            return varsInTemplate;
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
            final Templator templator = (Templator) object;
            return Objects.equals(template, templator.template) && Objects.equals(varsInTemplate,
                    templator.varsInTemplate) && Objects.equals(partExtractors, templator.partExtractors);
        }

        @Override
        public int hashCode() {
            return Objects.hash(template, varsInTemplate, partExtractors);
        }
    }


    // --------------------------------------------------------------------------------


    public static class GeneratorBuilder {

        private final Map<String, ReplacementProvider> varToReplacementProviderMap = new HashMap<>();
        private final Templator templator;

        private GeneratorBuilder(final Templator templator) {
            this.templator = templator;
        }

        /**
         * Add a simple static replacement for var.
         * This will override any existing replacement for var.
         * If var is not in the template it is a no-op.
         */
        public GeneratorBuilder addReplacement(final String var, final String replacement) {
            if (NullSafe.isNonBlankString(var)) {
                if (templator.isVarInTemplate(var)) {
                    if (NullSafe.isNonEmptyString(replacement)) {
                        // No point adding a func for an empty replacement
                        varToReplacementProviderMap.put(var, aVar -> replacement);
                    }
                } else {
                    LOGGER.debug("var '{}' is not in template '{}'", var, templator.template);
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
        public GeneratorBuilder addReplacements(final Map<String, String> replacementsMap) {
            NullSafe.map(replacementsMap)
                    .forEach((var, replacement) -> {
                        if (templator.isVarInTemplate(var)
                            && NullSafe.isNonEmptyString(replacement)) {
                            // No point adding a func for an empty replacement
                            varToReplacementProviderMap.put(var, aVar -> replacement);
                        }
                    });
            return this;
        }

        /**
         * Add a lazy {@link ReplacementProvider} for var.
         * replacementSupplier will only be called once to get a replacement
         * even if var appears more than once in the template.
         * If var is not in the template it is a no-op.
         */
        public GeneratorBuilder addLazyReplacement(final String var,
                                                   final Supplier<String> replacementSupplier) {
            Objects.requireNonNull(replacementSupplier);
            if (NullSafe.isNonBlankString(var)) {
                if (templator.isVarInTemplate(var)) {
                    final SingleStatefulReplacementProvider singleStatefulReplacementProvider =
                            new SingleStatefulReplacementProvider(var, replacementSupplier);
                    varToReplacementProviderMap.put(var, singleStatefulReplacementProvider);
                } else {
                    LOGGER.debug("var '{}' is not in template '{}'", var, templator.template);
                }
            } else {
                throw new IllegalArgumentException("Blank var");
            }
            return this;
        }

        /**
         * Add a {@link ReplacementProvider} function that will be used for ALL vars in the template.
         */
        public GeneratorBuilder addCommonReplacementFunction(final ReplacementProvider replacementProvider) {
            Objects.requireNonNull(replacementProvider);

            final ReplacementProvider statefulReplacementProvider = new CommonStatefulReplacementProvider(
                    replacementProvider);
            for (final String var : templator.getVarsInTemplate()) {
                varToReplacementProviderMap.put(var, statefulReplacementProvider);
            }
            return this;
        }

        /**
         * Generate the output string from the template.
         *
         * @return The String generated from replacing the variables in the template.
         */
        public String generate() {
            return templator.doGenerate(varToReplacementProviderMap);
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
    private interface PartExtractor extends Function<Map<String, ReplacementProvider>, String> {

        @Override
        String apply(Map<String, ReplacementProvider> stringReplacementProviderMap);
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
        public String apply(final Map<String, ReplacementProvider> varToReplacementProviderMap) {
            // Reuse the replacement across calls
            final ReplacementProvider replacementProvider = varToReplacementProviderMap.get(var);
            // Get the replacement from the replacementProvider, then format it
            return NullSafe.getOrElse(
                    replacementProvider,
                    aReplacementProvider -> aReplacementProvider.apply(var),
                    formatter,
                    "");
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
        public String apply(final Map<String, ReplacementProvider> ignored) {
            return staticText;
        }
    }
}
