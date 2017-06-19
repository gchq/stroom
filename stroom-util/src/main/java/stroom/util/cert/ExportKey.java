/*
 * Copyright 2016 Crown Copyright
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

import org.springframework.util.Base64Utils;

import java.io.FileInputStream;
import java.security.Key;
import java.security.KeyStore;
import java.util.HashMap;

/**
 * Utility to export a private key and certificate from a key store.
 *
 * E.g. java stroom.util.cert.ExportKey
 * keystore=/home/user01/keys/server.keystore keypass=changeit alias=smrs
 */
public class ExportKey {
    public static void main(String[] args) throws Exception {
        HashMap<String, String> argsMap = new HashMap<String, String>();
        for (int i = 0; i < args.length; i++) {
            String[] split = args[i].split("=");
            if (split.length > 1) {
                argsMap.put(split[0], split[1]);
            } else {
                argsMap.put(split[0], "");
            }
        }

        String keyPass = argsMap.get("keypass");
        String alias = argsMap.get("alias");
        String keystore = argsMap.get("keystore");

        KeyStore ks = KeyStore.getInstance("JKS", "SUN");
        ks.load(new FileInputStream(keystore), keyPass.toCharArray());

        Key key = ks.getKey(alias, keyPass.toCharArray());

        if (key == null) {
            System.out.println("No key with alias " + alias);
            return;
        }

        System.out.println("-----BEGIN PRIVATE KEY-----");
        System.out.println(Base64Utils.encodeToString(key.getEncoded()));
        System.out.println("-----END PRIVATE KEY-----");

        System.out.println("-----BEGIN CERTIFICATE-----");
        System.out.println(Base64Utils.encodeToString(ks.getCertificate(alias).getEncoded()));
        System.out.println("-----END CERTIFICATE-----");
    }
}
