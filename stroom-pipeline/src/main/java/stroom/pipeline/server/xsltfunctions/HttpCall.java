package stroom.pipeline.server.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Request.Builder;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import java.io.IOException;
import java.util.Objects;

@Component
@Scope(StroomScope.PROTOTYPE)
class HttpCall extends StroomExtensionFunctionCall {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpCall.class);

    private final OkHttpClient httpClient = new OkHttpClient();

    @Override
    protected Sequence call(String functionName, XPathContext context, Sequence[] arguments) throws XPathException {
        Sequence sequence = EmptyAtomicSequence.getInstance();

        final String url = arguments.length > 0 ? getSafeString(functionName, context, arguments, 0) : null;
        final String headers = arguments.length > 1 ? getSafeString(functionName, context, arguments, 1) : null;
        final String mediaType = arguments.length > 2 ? getSafeString(functionName, context, arguments, 2) : "application/json; charset=utf-8";
        final String data = arguments.length > 3 ? getSafeString(functionName, context, arguments, 3) : null;
        final boolean ignore = arguments.length > 4 ? getSafeBoolean(functionName, context, arguments, 4) : false;

        if (url == null) {
            log(context, Severity.WARNING, "No URL specified for HTTP call", null);

        } else {
            LOGGER.trace(() -> "Making HTTP call to: " + url);

            Builder builder = new Builder().url(url);

            if (data != null && data.length() > 0) {
                final RequestBody body = RequestBody.create(data, MediaType.parse(mediaType));
                builder = builder.post(body);
            }

            if (headers != null && headers.length() > 0) {
                final String[] parts = headers.split("\n");
                for (final String part : parts) {
                    int index = part.indexOf(": ");
                    if (index > 0) {
                        final String key = part.substring(0, index);
                        final String value = part.substring(index + 2);
                        builder = builder.addHeader(key, value);
                    }
                }
            }

            final Request request = builder.build();

            try (final Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    if (response.body() != null) {
                        sequence = StringValue.makeStringValue(Objects.requireNonNull(response.body()).string());
                    }
                } else {
                    LOGGER.trace(() -> "Error calling '" + url + "' " + response.code() + " " + response.message());
                    if (!ignore) {
                        log(context, Severity.WARNING, "Error calling '" + url + "' " + response.code() + " " + response.message(), null);
                    }
                }
            } catch (final IOException e) {
                LOGGER.trace(e::getMessage, e);
                if (!ignore) {
                    log(context, Severity.WARNING, "Could not make request", e);
                }
            }
        }

        return sequence;
    }
}
