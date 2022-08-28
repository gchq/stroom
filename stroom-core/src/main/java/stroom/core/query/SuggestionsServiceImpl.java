package stroom.core.query;

import stroom.query.shared.FetchSuggestionsRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import javax.inject.Singleton;

@Singleton
@SuppressWarnings("unused")
public class SuggestionsServiceImpl implements SuggestionsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuggestionsServiceImpl.class);

    private final Map<String, SuggestionsQueryHandler> queryHandlerMap = new HashMap<>();

    @Override
    public void registerHandler(final String dataSourceType, final SuggestionsQueryHandler handler) {
        queryHandlerMap.putIfAbsent(dataSourceType, handler);
    }

    @Override
    public List<String> fetch(final FetchSuggestionsRequest request) {
        final String dataSourceType = request.getDataSource().getType();

        if (dataSourceType != null && queryHandlerMap.containsKey(dataSourceType)) {
            final SuggestionsQueryHandler queryHandler = queryHandlerMap.get(dataSourceType);
            try {
                return queryHandler.getSuggestions(request.getDataSource(), request.getField(),
                        request.getText()).get();
            } catch (InterruptedException e) {
                return Collections.emptyList();
            } catch (ExecutionException e) {
                LOGGER.error("Failed to retrieve suggestions for field: " + request.getField(), e);
            }
        }

        return Collections.emptyList();
    }
}
