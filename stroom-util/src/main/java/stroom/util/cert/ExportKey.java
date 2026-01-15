/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.cert;

import org.apache.commons.codec.binary.Base64;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility to export a private key and certificate from a key store.
 * <p>
 * E.g. java stroom.util.ExportKey
 * keystore=/home/user01/keys/server.keystore keypass=changeit alias=smrs
 */
public class ExportKey {

    public static void main(final String[] args)
            throws KeyStoreException, IOException, CertificateException, NoSuchAlgorithmException,
            NoSuchProviderException, UnrecoverableKeyException {
        final Map<String, String> argsMap = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            final String[] split = args[i].split("=");
            if (split.length > 1) {
                argsMap.put(split[0], split[1]);
            } else {
                argsMap.put(split[0], "");
            }
        }

        final String keyPass = argsMap.get("keypass");
        final String alias = argsMap.get("alias");
        final String keystore = argsMap.get("keystore");

        final KeyStore ks = KeyStore.getInstance("JKS", "SUN");
        try (final InputStream inputStream = Files.newInputStream(Paths.get(keystore))) {
            ks.load(inputStream, keyPass.toCharArray());
        }

        final Key key = ks.getKey(alias, keyPass.toCharArray());

        if (key == null) {
            System.out.println("No key with alias " + alias);
            return;
        }

        System.out.println("-----BEGIN PRIVATE KEY-----");
        System.out.println(new String(Base64.encodeBase64(key.getEncoded())));
        System.out.println("-----END PRIVATE KEY-----");

        System.out.println("-----BEGIN CERTIFICATE-----");
        System.out.println(new String(Base64.encodeBase64(ks.getCertificate(alias).getEncoded())));
        System.out.println("-----END CERTIFICATE-----");
    }
}
