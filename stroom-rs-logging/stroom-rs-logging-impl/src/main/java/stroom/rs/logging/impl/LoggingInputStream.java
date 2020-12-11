package stroom.rs.logging.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.Optional;

import static stroom.rs.logging.impl.StroomServerLoggingFilterImpl.LOGGER;

class LoggingInputStream extends BufferedInputStream {
    private final static int MAX_ENTITY_SIZE = 64 * 1024 * 1024;
    private Object requestEntity;
    private final LoggingInfo loggingInfo;
    private final boolean constructed;

    public LoggingInputStream (final LoggingInfo loggingInfo, final InputStream original, final ObjectMapper objectMapper, final Charset charset) throws IOException {
        super(original);
        this.loggingInfo = loggingInfo;
        readEntity(objectMapper, charset);
        constructed = true;
    }

    private void readEntity(final ObjectMapper objectMapper, final Charset charset) throws IOException {
        if (loggingInfo != null){
            mark(MAX_ENTITY_SIZE + 1);
            Optional<Class<?>> requestClass = loggingInfo.getRequestParamClass();

            if (requestClass.isPresent()) {
                try {
                    requestEntity = objectMapper.readValue(new InputStreamReader(this, charset), requestClass.get());
                } catch (Exception ex){
                    //Indicates that this request type cannot be constructed in this way.
                    requestEntity = null;
                }
            }

            reset();
        }
    }

    public Object getRequestEntity() {
        return requestEntity;
    }

    public LoggingInfo getLoggingInfo() {
        return loggingInfo;
    }

    @Override
    public void close() throws IOException {
        if (constructed) {
            super.close();
        }
    }
}
