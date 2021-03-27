package stroom.search.elastic;

import stroom.crypto.shared.CryptoUtils;
import stroom.docstore.server.Serialiser;
import stroom.search.elastic.shared.ElasticCluster;
import stroom.search.elastic.shared.ElasticIndex;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ElasticClusterJsonSerialiser implements Serialiser<ElasticCluster> {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticClusterJsonSerialiser.class);
    private final ObjectMapper mapper;
    private final String secretEncryptionKey;

    public ElasticClusterJsonSerialiser(final String secretEncryptionKey) {
        this.secretEncryptionKey = secretEncryptionKey;
        this.mapper = createMapper();
    }

    private ObjectMapper createMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_NULL_MAP_VALUES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        return mapper;
    }

    @Override
    public ElasticCluster read(final InputStream inputStream, final Class<ElasticCluster> clazz) throws IOException {
        final ElasticCluster document = mapper.readValue(inputStream, clazz);
        final String apiKey = document.getConnectionConfig().getApiKeySecretEncrypted();
        String decryptedApiKey;

        // Decrypt the API key using the configured cluster secret
        try {
            decryptedApiKey = CryptoUtils.decrypt(apiKey, secretEncryptionKey);
        }
        catch (Exception e) {
            LOGGER.warn("Failed to decrypt API key for Elasticsearch cluster: '" + document.getName() + "'", e.getMessage());

            // Value is probably already decrypted, so use as-is
            decryptedApiKey = apiKey;
        }

        document.getConnectionConfig().setApiKeySecret(decryptedApiKey);

        return document;
    }

    @Override
    public void write(final OutputStream outputStream, final ElasticCluster document) throws IOException {
        write(outputStream, document, false);
    }

    @Override
    public void write(final OutputStream outputStream, final ElasticCluster document, final boolean export) throws IOException {
        final String apiKey = document.getConnectionConfig().getApiKeySecret();
        String encryptedApiKey;

        // Encrypt the API key with the configured cluster secret and write it to the destination stream
        try {
            encryptedApiKey = CryptoUtils.encrypt(apiKey, secretEncryptionKey);
        }
        catch (Exception e) {
            LOGGER.warn("Failed to encrypt API key for Elasticsearch cluster: '" + document.getName() + "'", e.getMessage());

            // Prevent plain-text key leaking to storage
            encryptedApiKey = "";
        }

        document.getConnectionConfig().setApiKeySecretEncrypted(encryptedApiKey);

        mapper.writeValue(outputStream, document);
    }
}
