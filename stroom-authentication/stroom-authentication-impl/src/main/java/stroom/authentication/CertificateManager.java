/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.authentication;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.authentication.exceptions.NoCertificateException;

import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

public class CertificateManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CertificateManager.class);

    public Optional<String> getCertificate(HttpServletRequest httpServletRequest) {
        String dn = httpServletRequest.getHeader("X-SSL-CLIENT-S-DN");
        LOGGER.debug("Found X-SSL-CLIENT-S-DN header: {}", dn);
        String cn = null;
        try {
            cn = getCn(dn);
        } catch (NoCertificateException e) {
            LOGGER.debug(e.getMessage());
        }
        return Optional.ofNullable(cn);
    }

    public String getCn(String dn) {
        if (dn == null) {
            throw new NoCertificateException();
        }

        Optional<String> cn;
        try {
            LdapName ldapName = new LdapName(dn);
            cn = ldapName.getRdns().stream()
                    .filter(rdn -> rdn.getType().equalsIgnoreCase("CN"))
                    .map(rdn -> (String) rdn.getValue())
                    .findFirst();

        } catch (InvalidNameException e) {
            String message = "Cannot process this DN. Redirecting to login.";
            LOGGER.debug(message, e);
            throw new NoCertificateException(message);
        }

        if (!cn.isPresent()) {
            throw new NoCertificateException();
        }

        return cn.get();
    }
}
