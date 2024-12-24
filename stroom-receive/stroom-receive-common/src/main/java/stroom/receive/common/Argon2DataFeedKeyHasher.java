package stroom.receive.common;

import stroom.receive.common.DataFeedKeyServiceImpl.DataFeedKeyHashAlgorithm;
import stroom.util.string.Base58;

import org.bouncycastle.crypto.generators.Argon2BytesGenerator;
import org.bouncycastle.crypto.params.Argon2Parameters;
import org.bouncycastle.crypto.params.Argon2Parameters.Builder;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

class Argon2DataFeedKeyHasher implements DataFeedKeyHasher {

    // WARNING!!!
    // Do not change any of these otherwise it will break hash verification of existing
    // keys. If you want to tune it, make a new ApiKeyHasher impl with a new getType()
    // 48, 2, 65_536, 1 => ~90ms per hash
    private static final int HASH_LENGTH = 48;
    private static final int ITERATIONS = 2;
    private static final int MEMORY_KB = 65_536;
    private static final int PARALLELISM = 1;

    private final Argon2Parameters argon2Parameters;

    public Argon2DataFeedKeyHasher() {
        // No salt given the length of api keys being hashed
        this.argon2Parameters = new Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withIterations(ITERATIONS)
                .withMemoryAsKB(MEMORY_KB)
                .withParallelism(PARALLELISM)
                .build();
    }

    @Override
    public String hash(final String dataFeedKey) {
        Objects.requireNonNull(dataFeedKey);
        Argon2BytesGenerator generate = new Argon2BytesGenerator();
        generate.init(argon2Parameters);
        byte[] result = new byte[HASH_LENGTH];
        generate.generateBytes(
                dataFeedKey.trim().getBytes(StandardCharsets.UTF_8),
                result,
                0,
                result.length);

        // Base58 is a bit less nasty than base64 and widely supported in other languages
        // due to use in bitcoin.
        return Base58.encode(result);
    }

    @Override
    public DataFeedKeyHashAlgorithm getAlgorithm() {
        return DataFeedKeyHashAlgorithm.ARGON2;
    }
}
