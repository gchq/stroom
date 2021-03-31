package stroom.search.elastic;

import stroom.config.app.CryptoConfig;
import stroom.crypto.shared.CryptoUtils;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.search.elastic.shared.ElasticClusterDoc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import javax.inject.Inject;

class ElasticClusterSerialiser implements DocumentSerialiser2<ElasticClusterDoc> {

    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticClusterSerialiser.class);
    private final Serialiser2<ElasticClusterDoc> delegate;
    private final String secretEncryptionKey;

    @Inject
    ElasticClusterSerialiser(
            final Serialiser2Factory serialiser2Factory,
            final CryptoConfig cryptoConfig
    ) {
        this.delegate = serialiser2Factory.createSerialiser(ElasticClusterDoc.class);
        this.secretEncryptionKey = cryptoConfig.getSecretEncryptionKey();
    }

    @Override
    public ElasticClusterDoc read(final Map<String, byte[]> data) throws IOException {
        final ElasticClusterDoc document = delegate.read(data);
        final String apiKey = document.getConnectionConfig().getApiKeySecretEncrypted();
        String decryptedApiKey;

        // Decrypt the API key using the configured cluster secret
        try {
            decryptedApiKey = CryptoUtils.decrypt(apiKey, secretEncryptionKey);
        } catch (Exception e) {
            LOGGER.warn("Failed to decrypt API key for Elasticsearch cluster: '" + document.getName() + "'",
                    e.getMessage());

            // Value is probably already decrypted, so use as-is
            decryptedApiKey = apiKey;
        }

        document.getConnectionConfig().setApiKeySecret(decryptedApiKey);

        return document;
    }

    @Override
    public Map<String, byte[]> write(final ElasticClusterDoc document) throws IOException {
        final String apiKey = document.getConnectionConfig().getApiKeySecret();
        String encryptedApiKey;

        // Encrypt the API key with the configured cluster secret and write it to the destination stream
        try {
            encryptedApiKey = CryptoUtils.encrypt(apiKey, secretEncryptionKey);
        } catch (Exception e) {
            LOGGER.warn("Failed to encrypt API key for Elasticsearch cluster: '" + document.getName() + "'",
                    e.getMessage());

            // Prevent plain-text key leaking to storage
            encryptedApiKey = "";
        }

        document.getConnectionConfig().setApiKeySecretEncrypted(encryptedApiKey);

        return delegate.write(document);
    }
}
