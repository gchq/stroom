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
            final List<Object> funcList = new ArrayList<>();
            final Set<String> varsInTemplate = new HashSet<>();
            final Map<String, PartExtractor> varToPartExtractorMap = new HashMap<>();
            final StringBuilder sb = new StringBuilder();
            char lastChar = 0;
            boolean inVariable = false;
//            final Function<String, String> effectiveParamFormatter = replacementFormatter != null
//                    ? str -> replacementFormatter.apply(NullSafe.string(str))
//                    : NullSafe::string;

            for (final char chr : template.toCharArray()) {
                if (chr == '{' && lastChar == '$') {
                    inVariable = true;
                    if (!sb.isEmpty()) {
                        // Stuff before must be static text
                        final String staticText = format(sb.toString(), staticTextFormatter);
                        funcList.add(staticText);
                        LOGGER.debug("Adding static text func for '{}'", staticText);
                        sb.setLength(0);
                    }
                } else if (inVariable && chr == '}') {
                    inVariable = false;
                    final String var = sb.toString();
                    varsInTemplate.add(var);
                    final PartExtractor partExtractor = varToPartExtractorMap.computeIfAbsent(var, DynamicPart::new);
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
                funcList.add(staticText);
                sb.setLength(0);
            }
            return new Templator(template, varsInTemplate, funcList, replacementFormatter);
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
                Collections.emptyList(),
                null);

        /**
         * Here for debugging and toString
         */
        private final String template;
        private final Set<String> varsInTemplate;

        /**
         * Items are either a String or a {@link DynamicPart}. Object is used to avoid
         * having to wrap the simple strings with another object.
         */
        private final List<Object> partExtractors;
        private final int partCount;
        private final Function<String, String> replacementFormatter;

        private Templator(final String template,
                          final Set<String> varsInTemplate,
                          final List<Object> partExtractors,
                          final Function<String, String> replacementFormatter) {
            this.template = template;
            this.varsInTemplate = NullSafe.unmodifialbeSet(varsInTemplate);
            this.partExtractors = NullSafe.unmodifiableList(partExtractors);
            this.replacementFormatter = replacementFormatter;
            this.partCount = partExtractors.size();
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
//            LOGGER.debug("Generated output '{}' from varToReplacementProviderMap: {}",
//                    output, varToReplacementMap);
            return output;
        }

        private String doGenerate(final Map<String, Object> varToReplacementMap) {
            // partExtractors cope with null map
            final String output;
            if (partExtractors.isEmpty()) {
                output = "";
            } else {
                final String[] parts = new String[partCount];
                for (int i = 0; i < partExtractors.size(); i++) {
                    final Object partExtractor = partExtractors.get(i);
                    final String part = switch (partExtractor) {
                        case final String str -> str;
                        case final DynamicPart dynamicPart -> NullSafe.string(dynamicPart.apply(varToReplacementMap));
                        case null, default -> "";
                    };
                    parts[i] = part;
                }
                return String.join("", parts);
            }
//            LOGGER.debug("Generated output '{}' from varToReplacementProviderMap: {}", output, varToReplacementMap);
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

        // Value is either a String or a ReplacementProvider. Object is used to avoid
        // having to wrap the simple strings in another object
        private final Map<String, Object> varToReplacementMap = new HashMap<>();
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
                    putReplacement(var, replacement);
                } else {
//                    LOGGER.debug("var '{}' is not in template '{}'", var, templator.template);
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
                        if (templator.isVarInTemplate(var)) {
                            putReplacement(var, replacement);
                        }
                    });
            return this;
        }

        private void putReplacement(final String var, final String replacement) {
            if (NullSafe.isNonEmptyString(replacement)) {
                // No point adding a replacement for an empty replacement
                if (templator.replacementFormatter != null) {
                    // Format the replacement
                    varToReplacementMap.put(
                            var,
                            NullSafe.string(templator.replacementFormatter.apply(replacement)));
                } else {
                    varToReplacementMap.put(var, replacement);
                }
            }
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
                    putReplacement(var, replacementSupplier.get());
                } else {
//                    LOGGER.debug("var '{}' is not in template '{}'", var, templator.template);
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
                    replacementProvider,
                    templator.replacementFormatter);

            for (final String var : templator.getVarsInTemplate()) {
                varToReplacementMap.put(var, statefulReplacementProvider);
            }
            return this;
        }

        /**
         * Generate the output string from the template.
         *
         * @return The String generated from replacing the variables in the template.
         */
        public String generate() {
            return templator.doGenerate(varToReplacementMap);
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


    /**
     * One replacement provider for all vars, e.g. it can wrap the lookup of a var in an AttributeMap.
     */
    private static class CommonStatefulReplacementProvider implements ReplacementProvider {

        private final ReplacementProvider replacementProvider;
        private final Function<String, String> replacementFormatter;
        private final Map<String, String> replacements = new HashMap<>();

        private CommonStatefulReplacementProvider(final ReplacementProvider replacementProvider,
                                                  final Function<String, String> replacementFormatter) {
            this.replacementProvider = replacementProvider;
            this.replacementFormatter = replacementFormatter;
        }

        @Override
        public String apply(final String var) {
            Objects.requireNonNull(var);
            String replacement = replacements.get(var);
            if (replacement == null) {
                replacement = NullSafe.string(replacementProvider.apply(var));
                if (replacementFormatter != null) {
                    replacement = NullSafe.string(replacementFormatter.apply(replacement));
                }
                replacements.put(var, replacement);
            }
            return replacement;
        }
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    private interface PartExtractor extends Function<Map<String, Object>, String> {

        @Override
        String apply(Map<String, Object> stringReplacementProviderMap);
    }


    // --------------------------------------------------------------------------------


    private record DynamicPart(String var) implements PartExtractor {

        @Override
        public String apply(final Map<String, Object> varToReplacementProviderMap) {
            // Reuse the replacement across calls
            final Object obj = varToReplacementProviderMap.get(var);

            final String replacement = switch (obj) {
                case final String str -> str;
                case final ReplacementProvider aReplacementProvider -> aReplacementProvider.apply(var);
                case null, default -> "";
            };
            return NullSafe.string(replacement);
        }

        @Override
        public String toString() {
            return "DynamicPart{" +
                   "var='" + var + '\'' +
                   '}';
        }
    }
}
