package stroom.util.string;

import stroom.util.NullSafe;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
     * @param template            The template string to parse.
     * @param paramFormatter      An optional formatter that will be called on all
     *                            parameterised parts of the resolved string,
     *                            e.g. {@link String#toUpperCase()} and/or to replace unwanted chars.
     * @param staticTextFormatter An optional formatter that will be called on all
     *                            static parts of the template
     *                            e.g. {@link String#toUpperCase()} and/or to replace unwanted chars.
     */
    public static Templator parseTemplate(final String template,
                                          final Function<String, String> paramFormatter,
                                          final Function<String, String> staticTextFormatter) {
        if (NullSafe.isEmptyString(template)) {
            return Templator.EMPTY_TEMPLATE;
        } else {
            final List<PartExtractor> funcList = new ArrayList<>();
            final StringBuilder sb = new StringBuilder();
            char lastChar = 0;
            boolean inVariable = false;
            final Function<String, String> effectiveParamFormatter = paramFormatter != null
                    ? str -> paramFormatter.apply(NullSafe.string(str))
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
                    final String key = sb.toString();
                    funcList.add(attributeMap ->
                            NullSafe.hasEntries(attributeMap)
                                    ? effectiveParamFormatter.apply(attributeMap.get(key))
                                    : "");
                    LOGGER.debug("Adding header attributeMap value func for key '{}'", key);
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
            return new Templator(funcList);
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

        private static final Templator EMPTY_TEMPLATE = new Templator(Collections.emptyList());

        private final List<PartExtractor> partExtractors;

        private Templator(final List<PartExtractor> partExtractors) {
            this.partExtractors = NullSafe.list(partExtractors);
        }

        /**
         * Use the values in map to derive a string from the parsed template.
         */
        public String apply(final Map<String, String> map) {
            // partExtractors cope with null map
            final String name;
            if (partExtractors.isEmpty()) {
                name = "";
            } else {
                name = partExtractors.stream()
                        .map(func -> func.apply(map))
                        .collect(Collectors.joining());
            }

            LOGGER.debug("Generated name '{}' from attributeMap: {}", name, map);
            return name;
        }
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    private interface PartExtractor extends Function<Map<String, String>, String> {

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
        public String apply(final Map<String, String> ignored) {
            return staticText;
        }
    }
}
