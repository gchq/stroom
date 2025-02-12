package stroom.receive.common;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.util.NullSafe;
import stroom.util.concurrent.CachedValue;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Checks the Feed and Type attributes are supplied. If auto content creation is enabled
 * then the Feed can be derived from AccountId|Format|SchemaName|Type.
 */
public class FeedNameCheckAttributeMapFilter implements AttributeMapFilter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FeedNameCheckAttributeMapFilter.class);

    private static final Pattern PARAM_REPLACE_PATTERN = Pattern.compile("[^A-Z0-9_]");
    private static final Pattern STATIC_REPLACE_PATTERN = Pattern.compile("[^A-Z0-9_-]");
//    public static final String NAME_PART_DELIMITER = "-";

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final CachedValue<FeedNameGenerator, ConfigState> cachedFeedNameGenerator;

    @Inject
    public FeedNameCheckAttributeMapFilter(final Provider<ReceiveDataConfig> receiveDataConfigProvider) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.cachedFeedNameGenerator = new CachedValue<>(
                Duration.ofMinutes(1),
                FeedNameGenerator::new,
                () -> ConfigState.fromConfig(receiveDataConfigProvider.get()));
    }

    @Override
    public boolean filter(final AttributeMap attributeMap) {
        // Get the type name from the header arguments if supplied.
        final String type = NullSafe.trim(attributeMap.get(StandardHeaderArguments.TYPE));
        if (!type.isEmpty() && !getValidStreamTypes().contains(type)) {
            throw new StroomStreamException(StroomStatusCode.INVALID_TYPE, attributeMap);
        }

        String feedName = NullSafe.trim(attributeMap.get(StandardHeaderArguments.FEED));
        if (receiveDataConfigProvider.get().isFeedNameGenerationEnabled()) {
            LOGGER.debug("feedNameGenerationEnabled");
            // If they supply a feed then go with that
            if (feedName.isEmpty()) {
                LOGGER.debug("No feed name supplied");
                final String accountName = NullSafe.trim(attributeMap.get(StandardHeaderArguments.ACCOUNT_ID));
                if (accountName.isEmpty()) {
                    throw new StroomStreamException(StroomStatusCode.ACCOUNT_ID_MUST_BE_SPECIFIED, attributeMap);
                }
                final String component = NullSafe.trim(attributeMap.get(StandardHeaderArguments.COMPONENT));
                if (component.isEmpty()) {
                    throw new StroomStreamException(StroomStatusCode.COMPONENT_MUST_BE_SPECIFIED, attributeMap);
                }
                feedName = cachedFeedNameGenerator.getValue()
                        .generateName(attributeMap);
//                feedName = deriveFeedName(attributeMap, type);
                // Add the derived feed name as everything else depends on the feed name
                attributeMap.put(StandardHeaderArguments.FEED, feedName);
            }
        } else {
            if (feedName.isEmpty()) {
                throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap);
            }
        }

        return true;
    }

    private Set<String> getValidStreamTypes() {
        return NullSafe.set(receiveDataConfigProvider.get().getMetaTypes());
    }

    //pkg private for testing
//    static String deriveFeedName(final AttributeMap attributeMap,
//                                 final String type) {
//
//        final String accountId = NullSafe.trim(attributeMap.get(StandardHeaderArguments.ACCOUNT_ID));
//        if (accountId.isEmpty()) {
//            throw new StroomStreamException(StroomStatusCode.ACCOUNT_ID_MUST_BE_SPECIFIED, attributeMap);
//        }
//
//        final String component = NullSafe.trim(attributeMap.get(StandardHeaderArguments.COMPONENT));
//        final String format = NullSafe.trim(attributeMap.get(StandardHeaderArguments.FORMAT));
//        final String schema = NullSafe.trim(attributeMap.get(StandardHeaderArguments.SCHEMA));
//        String effectiveType = type.isBlank()
//                ? StreamTypeNames.EVENTS
//                : type;
//
//        final List<String> nameParts = List.of(
//                accountId,
//                component,
//                schema,
//                format,
//                effectiveType);
//
//        final String feedName = buildName(nameParts);
//        LOGGER.debug("Derived feed name: '{}' from name parts: {}, attributeMap: {}",
//                feedName, nameParts, attributeMap);
//        return feedName;
//    }

//    private static String buildName(final List<String> parts) {
//        return parts.stream()
//                .filter(Predicate.not(String::isBlank))
//                .map(FeedNameCheckAttributeMapFilter::normaliseNamePart)
//                .collect(Collectors.joining(NAME_PART_DELIMITER));
//    }

//    private static String normaliseNamePart(final String name) {
//        String result = NullSafe.trim(name);
//        result = result.toUpperCase();
//        result = PARAM_REPLACE_PATTERN.matcher(result).replaceAll("_");
//        return result;
//    }

    private static String normaliseParam(final String name) {
        String result = NullSafe.trim(name);
        result = result.toUpperCase();
        result = PARAM_REPLACE_PATTERN.matcher(result).replaceAll("_");
        return result;
    }

    private static String normaliseStaticText(final String name) {
        String result = NullSafe.trim(name);
        result = result.toUpperCase();
        result = STATIC_REPLACE_PATTERN.matcher(result).replaceAll("_");
        return result;
    }


    // --------------------------------------------------------------------------------


    /**
     * Pkg private for testing
     */
    record ConfigState(boolean feedNameGenerationEnabled,
                       String feedNameTemplate,
                       Set<String> feedNameGenerationMandatoryHeaders) {

        static ConfigState fromConfig(final ReceiveDataConfig receiveDataConfig) {
            return new ConfigState(
                    receiveDataConfig.isFeedNameGenerationEnabled(),
                    receiveDataConfig.getFeedNameTemplate(),
                    receiveDataConfig.getFeedNameGenerationMandatoryHeaders());
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * Pkg private for testing
     */
    static class FeedNameGenerator {

        private final ConfigState configState;
        private final List<Function<AttributeMap, String>> partExtractors;
//        private final Function<String, String> deDupFunc;

        public FeedNameGenerator(final ConfigState configState) {
            this.configState = configState;
            if (configState.feedNameGenerationEnabled) {
                try {
                    partExtractors = parseTemplate(configState.feedNameTemplate);
                } catch (Exception e) {
                    throw new IllegalArgumentException(LogUtil.message(
                            "Error parsing feed name template '{}'", configState.feedNameTemplate));
                }
            } else {
                partExtractors = List.of(attributeMap ->
                        NullSafe.trim(attributeMap.get(StandardHeaderArguments.FEED)));
            }
//            if (NullSafe.isEmptyString(configState.feedNameDeDupCharacters)) {
//                deDupFunc = Function.identity();
//            } else {
//                deDupFunc = buildDeDupFunc(configState.feedNameDeDupCharacters);
//            }
        }

        public String generateName(final AttributeMap attributeMap) {
            final String name = partExtractors.stream()
                    .map(func -> func.apply(attributeMap))
                    .collect(Collectors.joining());
//            name = deDupFunc.apply(name);
            LOGGER.debug("Generated name '{}' from attributeMap: {}", name, attributeMap);
            return name;
        }

//        private Function<String, String> buildDeDupFunc(final String deDupChars) {
//            final StringBuilder sb = new StringBuilder();
//            sb.append("[");
//
//            for (final char chr : deDupChars.toCharArray()) {
//                final String escaped = Pattern.quote(String.valueOf(chr));
//                sb.append(escaped);
//            }
//            sb.append("]");
//            final String charClass = sb.toString();
//            final Pattern deDupPattern = Pattern.compile("((" + charClass + ")\\2+)");
//            final Pattern leadingTrailingPattern = Pattern.compile(
//                    "(^(" + charClass + ")\\2*|(" + charClass + ")\\3*$)");
//
//            LOGGER.debug("deDupPattern: '{}', leadingTrailingPattern: '{}'",
//                    deDupPattern, leadingTrailingPattern);
//
//            return str -> {
//                if (NullSafe.isEmptyString(str)) {
//                    return "";
//                } else {
//
//                    String output = deDupPattern.matcher(str)
//                            .replaceAll(matchResult -> {
//                                // Replace '-----' with '-'
//                                return matchResult.group(2);
//                            });
//                    output = leadingTrailingPattern.matcher(output)
//                            .replaceAll("");
//                    return output;
//                }
//            };
//        }

        /**
         * Compile the template into a list of functions that convert an {@link AttributeMap} into
         * a name part. On generation of a name, each function is called in turned and the outputs
         * concatenated together.
         */
        private List<Function<AttributeMap, String>> parseTemplate(final String template) {
            if (NullSafe.isEmptyString(template)) {
                return Collections.emptyList();
            } else {
                final List<Function<AttributeMap, String>> funcList = new ArrayList<>();
                final StringBuilder sb = new StringBuilder();
                char lastChar = 0;
                boolean inVariable = false;
                for (final char chr : template.toCharArray()) {
                    if (chr == '$') {
                        // Skip over
                    } else if (chr == '{' && lastChar == '$') {
                        inVariable = true;
                        if (!sb.isEmpty()) {
                            // Stuff before must be static text
                            final String staticText = normaliseStaticText(sb.toString());
                            funcList.add(attributeMap -> staticText);
                            LOGGER.debug("Adding static text func for '{}'", staticText);
                            sb.setLength(0);
                        }
                    } else if (inVariable && chr == '}') {
                        inVariable = false;
                        final String key = sb.toString();
                        funcList.add(attributeMap -> {
                            if (attributeMap == null || attributeMap.isEmpty()) {
                                return "";
                            } else {
                                return normaliseParam(attributeMap.get(key));
                            }
                        });
                        LOGGER.debug("Adding header attributeMap value func for key '{}'", key);
                        sb.setLength(0);
                    } else {
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
                    final String staticText = normaliseStaticText(sb.toString());
                    funcList.add(attributeMap -> staticText);
                    sb.setLength(0);
                }
                return funcList;
            }
        }

        @Override
        public String toString() {
            return "FeedNameGenerator{" +
                   "configState=" + configState +
                   '}';
        }
    }
}
