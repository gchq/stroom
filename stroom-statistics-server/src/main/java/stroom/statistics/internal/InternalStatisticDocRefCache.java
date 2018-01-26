package stroom.statistics.internal;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.query.api.v2.DocRef;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class InternalStatisticDocRefCache {

    static final String PROP_KEY_FORMAT = "stroom.internalstatistics.%s.docRefs";
    private static final Logger LOGGER = LoggerFactory.getLogger(InternalStatisticDocRefCache.class);
    private static final Pattern DOC_REF_PART_PATTERN = Pattern.compile("(docRef\\([^,]+,[0-9a-f\\-]+,[^,]+\\))");
    private static final Pattern DOC_REF_WHOLE_PATTERN = Pattern.compile("(" + DOC_REF_PART_PATTERN.pattern() + ",?)+");

    private final StroomPropertyService stroomPropertyService;
    //map of internal stat key to the list of datasource docrefs available for that stat
    private final ConcurrentMap<String, List<DocRef>> map = new ConcurrentHashMap<>();

    @Inject
    public InternalStatisticDocRefCache(final StroomPropertyService stroomPropertyService) {
        this.stroomPropertyService = stroomPropertyService;
    }

    /**
     * @param internalStatisticKey
     * @return A list of {@link DocRef} objects for the given key.  Will return an empty list if no docRefs
     * exist for the key
     */
    List<DocRef> getDocRefs(final String internalStatisticKey) {
        Preconditions.checkNotNull(internalStatisticKey);
        return map.computeIfAbsent(internalStatisticKey, this::getDocRefsFromProperty);
    }

    private List<DocRef> getDocRefsFromProperty(final String internalStatisticKey) {
        String propKey = String.format(PROP_KEY_FORMAT, internalStatisticKey);

        String docRefsStr = stroomPropertyService.getProperty(propKey);

        if (docRefsStr == null || docRefsStr.isEmpty()) {
            LOGGER.trace("Returning empty list");
            return Collections.emptyList();
        } else {
            Matcher matcher = DOC_REF_WHOLE_PATTERN.matcher(docRefsStr);
            if (!matcher.matches()) {
                throw new RuntimeException(String.format("Property value for key %s does not contain valid docRefs [%s]", internalStatisticKey, docRefsStr));
            } else {
                List<DocRef> docRefs = splitString(docRefsStr);
                docRefs.forEach(docRef ->
                        LOGGER.info("Associating internal statistic [{}] with docRef [{} {} {}]",
                                internalStatisticKey, docRef.getType(), docRef.getUuid(), docRef.getName()));

                LOGGER.trace("Returning {}", docRefs);
                return docRefs;
            }
        }
    }

    private List<DocRef> splitString(final String propValue) {
        return getDocRefParts(propValue).stream()
                .map(part -> part.substring(7, part.length() - 1))
                .map(partInsideBrackets -> partInsideBrackets.split(","))
                .map(parts -> new DocRef(parts[0], parts[1], parts[2]))
                .collect(Collectors.toList());
    }

    private List<String> getDocRefParts(final String propValue) {
        Matcher matcher = DOC_REF_PART_PATTERN.matcher(propValue);

        List<String> parts = new ArrayList<>();
        while (matcher.find()) {
            parts.add(matcher.group(1));
        }
        return parts;
    }
}
