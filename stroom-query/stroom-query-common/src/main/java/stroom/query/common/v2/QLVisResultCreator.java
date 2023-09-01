package stroom.query.common.v2;

import stroom.query.api.v2.FlatResult;
import stroom.query.api.v2.QLVisResult;
import stroom.query.api.v2.QLVisSettings;
import stroom.query.api.v2.Result;
import stroom.query.api.v2.ResultRequest;
import stroom.query.util.LambdaLogger;
import stroom.query.util.LambdaLoggerFactory;
import stroom.util.string.ExceptionStringUtil;

import java.util.Collections;
import java.util.List;

public class QLVisResultCreator implements ResultCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(QLVisResultCreator.class);

    private final FlatResultCreator resultCreator;
    private final QLVisSettings visSettings;

    public QLVisResultCreator(final FlatResultCreator resultCreator,
                              final QLVisSettings visSettings) {
        this.resultCreator = resultCreator;
        this.visSettings = visSettings;
    }

    @Override
    public Result create(final DataStore dataStore, final ResultRequest resultRequest) {
        final FlatResult flatResult = (FlatResult) resultCreator.create(dataStore, resultRequest);
        return mapVisResult(visSettings, flatResult);
    }

    private QLVisResult mapVisResult(final QLVisSettings visSettings, final FlatResult result) {
        String json = null;
        List<String> errors = result.getErrors();
        try {
            json = new VisJson().createJson(result);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            errors = Collections.singletonList(ExceptionStringUtil.getMessage(e));
        }

        return new QLVisResult(result.getComponentId(), visSettings, json, result.getSize(), errors);
    }
}
