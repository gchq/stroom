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


import org.junit.jupiter.api.Test;
import stroom.util.test.StroomUnitTest;

import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TestCertificateUtil extends StroomUnitTest {
    @Test
    void testSpaceInCN() {
        final String dn = "CN=John Smith (johnsmith), OU=ouCode1, OU=ouCode2, O=oValue, C=GB";

        assertThat(CertificateUtil.dnToRfc2253(dn)).isEqualTo("CN=John Smith (johnsmith),OU=ouCode1,OU=ouCode2,O=oValue,C=GB");
        assertThat(CertificateUtil.extractCNFromDN(dn)).isEqualTo("John Smith (johnsmith)");
        assertThat(CertificateUtil.extractUserIdFromCN(CertificateUtil.extractCNFromDN(dn))).isEqualTo("johnsmith");

        final Pattern pattern = Pattern.compile("CN=[^ ]+ [^ ]+ \\(?([a-zA-Z0-9]+)\\)?");
        assertThat(CertificateUtil.extractUserIdFromDN(dn, pattern)).isEqualTo("johnsmith");
    }
}
