package stroom.core.query;

import stroom.query.shared.FetchSuggestionsRequest;
import stroom.security.api.SecurityContext;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
@SuppressWarnings("unused")
public class SuggestionsServiceImpl implements SuggestionsService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SuggestionsServiceImpl.class);

    private final SecurityContext securityContext;

    private final Map<String, SuggestionsQueryHandler> queryHandlerMap = new HashMap<>();

    @Inject
    SuggestionsServiceImpl(final SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    @Override
    public void registerHandler(final String dataSourceType, final SuggestionsQueryHandler handler) {
        queryHandlerMap.putIfAbsent(dataSourceType, handler);
    }

    @Override
    public List<String> fetch(final FetchSuggestionsRequest request) {
        final String dataSourceType = request.getDataSource().getType();

        return securityContext.secureResult(() -> {
            if (dataSourceType != null && queryHandlerMap.containsKey(dataSourceType)) {
                final SuggestionsQueryHandler queryHandler = queryHandlerMap.get(dataSourceType);
                return queryHandler.getSuggestions(request.getDataSource(), request.getField(),
                        request.getText());
            }
            return Collections.emptyList();
        });
    }
}
