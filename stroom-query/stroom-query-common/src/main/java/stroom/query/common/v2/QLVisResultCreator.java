package stroom.query.common.v2;

import stroom.query.api.FlatResult;
import stroom.query.api.QLVisResult;
import stroom.query.api.QLVisSettings;
import stroom.query.api.Result;
import stroom.query.api.ResultRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ErrorMessage;
import stroom.util.shared.Severity;
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
        final Result result = resultCreator.create(dataStore, resultRequest);
        if (result instanceof final FlatResult flatResult) {
            return mapVisResult(visSettings, flatResult);
        }
        return null;
    }

    private QLVisResult mapVisResult(final QLVisSettings visSettings, final FlatResult result) {
        String json = null;
        List<ErrorMessage> errorMessages = result.getErrorMessages();
        try {
            json = new VisJson().createJson(result);
        } catch (final RuntimeException e) {
            LOGGER.debug(e::getMessage, e);
            errorMessages = Collections.singletonList(
                    new ErrorMessage(Severity.ERROR, ExceptionStringUtil.getMessage(e)));
        }

        return new QLVisResult(result.getComponentId(), visSettings, json, result.getSize(), null, errorMessages);
    }
}
