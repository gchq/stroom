package stroom.security.api;

import stroom.security.shared.HashAlgorithm;

public interface HashFunctionFactory {

    HashFunction getHashFunction(final HashAlgorithm hashAlgorithm);

}
