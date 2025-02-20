package stroom.contentindex;

import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.ContentIndexable;
import stroom.explorer.shared.DocContentHighlights;
import stroom.explorer.shared.DocContentMatch;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.explorer.shared.StringMatchLocation;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class BasicContentIndex implements ContentIndex {

    private static final int MAX_HIGHLIGHTS = 100;

    private final Map<String, ContentIndexable> indexableMap;

    @Inject
    public BasicContentIndex(final Set<ContentIndexable> indexables) {
        final Map<String, ContentIndexable> indexableMap = new HashMap<>();
        for (final ContentIndexable indexable : indexables) {
            indexableMap.put(indexable.getType(), indexable);
        }
        this.indexableMap = indexableMap;
    }

    @Override
    public ResultPage<DocContentMatch> findInContent(final FindInContentRequest request) {
        final PageRequest pageRequest = request.getPageRequest();
        final List<DocContentMatch> matches = new ArrayList<>();
        final AtomicLong total = new AtomicLong();

        final StringMatcher stringMatcher = new StringMatcher(request.getFilter());
        indexableMap.values().forEach(indexable -> indexable.listDocuments().forEach(docRef ->
                indexable.getIndexableData(docRef).forEach((extension, text) -> {
                    final Optional<StringMatchLocation> optional = stringMatcher.match(text);
                    optional.ifPresent(match -> {
                        if (total.get() >= pageRequest.getOffset() &&
                            total.get() < pageRequest.getOffset() + pageRequest.getLength()) {
                            matches.add(DocContentMatch.create(docRef, extension, text, match));
                        }
                        total.incrementAndGet();
                    });
                })));

        return new ResultPage<>(matches, PageResponse
                .builder()
                .offset(pageRequest.getOffset())
                .length(matches.size())
                .total(total.get())
                .exact(total.get() == pageRequest.getOffset() + matches.size())
                .build());
    }

    @Override
    public DocContentHighlights fetchHighlights(final FetchHighlightsRequest request) {
        final ContentHighlighter highlighter = new BasicContentHighlighter(request.getFilter());
        final ContentIndexable indexable = indexableMap.get(request.getDocRef().getType());
        if (indexable != null) {
            final Map<String, String> map = indexable.getIndexableData(request.getDocRef());
            if (map != null) {
                final String data = map.get(request.getExtension());
                if (data != null) {
                    final List<StringMatchLocation> matchList = highlighter.getHighlights(data, MAX_HIGHLIGHTS);
                    if (!matchList.isEmpty()) {
                        return new DocContentHighlights(request.getDocRef(), data, matchList);
                    }
                }
            }
        }

        return null;
    }

    @Override
    public void reindex() {

    }
}
