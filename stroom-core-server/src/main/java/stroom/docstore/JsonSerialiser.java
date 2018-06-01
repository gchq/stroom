package stroom.docstore;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * THIS CLASS IS HERE PURELY TO MAINTAIN BACKWARDS COMPATIBILITY WITH THE V6_0_0_21__Dictionary CLASS
 */
public final class JsonSerialiser<D> implements Serialiser<D> {
    private final ObjectMapper mapper;

    public JsonSerialiser() {
        this.mapper = getMapper(true);
    }

    @Override
    public D read(final InputStream inputStream, final Class<D> clazz) throws IOException {
        return mapper.readValue(inputStream, clazz);
    }

    @Override
    public void write(final OutputStream outputStream, final D document) throws IOException {
        mapper.writeValue(outputStream, document);
    }

    private ObjectMapper getMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_NULL);
        // Enabling default typing adds type information where it would otherwise be ambiguous, i.e. for abstract classes
//        mapper.enableDefaultTyping();
        return mapper;
    }
}
