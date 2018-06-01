package stroom.docstore;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

public class JsonSerialiser2<D> implements Serialiser2<D> {
    private static final String META = "meta";

    private final Class<D> clazz;
    private final ObjectMapper mapper;

    public JsonSerialiser2(final Class<D> clazz) {
        this.clazz = clazz;
        this.mapper = getMapper(true);
    }

    @Override
    public D read(final Map<String, byte[]> data) throws IOException {
        final byte[] meta = data.get(META);
        return mapper.readValue(new StringReader(EncodingUtil.asString(meta)), clazz);
    }

    @Override
    public Map<String, byte[]> write(final D document) throws IOException {
        final StringWriter stringWriter = new StringWriter();
        mapper.writeValue(stringWriter, document);
        final Map<String, byte[]> data = new HashMap<>();
        data.put(META, EncodingUtil.asBytes(stringWriter.toString()));
        return data;
    }

    protected ObjectMapper getMapper(final boolean indent) {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, indent);
        mapper.setSerializationInclusion(Include.NON_EMPTY);
        // Enabling default typing adds type information where it would otherwise be ambiguous, i.e. for abstract classes
//        mapper.enableDefaultTyping();
        return mapper;
    }
}
