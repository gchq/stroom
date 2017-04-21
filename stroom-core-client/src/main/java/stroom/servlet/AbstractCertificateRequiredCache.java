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

package stroom.servlet;

import com.googlecode.ehcache.annotations.Cacheable;
import com.googlecode.ehcache.annotations.KeyGenerator;
import org.apache.log4j.Logger;
import org.springframework.util.StringUtils;
import stroom.node.server.StroomPropertyService;
import stroom.util.cert.CertificateUtil;
import stroom.util.shared.EqualsBuilder;
import stroom.util.spring.StroomStartup;

import javax.annotation.Resource;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bean to check
 */
public abstract class AbstractCertificateRequiredCache {
    private static Logger LOGGER = Logger.getLogger(AbstractCertificateRequiredCache.class);

    public static final String CERTIFICATE_CN = "certificateCn";
    public static final int NEXT_MS_INCREMENT_CHECK = 1000 * 60;

    private long certificateCnMatchRefershMs = 0;
    private String certificateCnMatchRegEx;
    private Pattern certificateCnMatch;

    @Resource
    private StroomPropertyService stroomPropertyService;

    protected abstract String getFilterProperty();

    public void checkForRegEx() {
        final long timeNowMs = System.currentTimeMillis();
        if (timeNowMs > certificateCnMatchRefershMs) {
            // Don't check for another min
            certificateCnMatchRefershMs = NEXT_MS_INCREMENT_CHECK + timeNowMs;

            final String newCertificateCnMatchRegEx = stroomPropertyService.getProperty(getFilterProperty());
            final EqualsBuilder equalsBuilder = new EqualsBuilder();
            equalsBuilder.append(certificateCnMatchRegEx, newCertificateCnMatchRegEx);
            if (equalsBuilder.isEquals()) {
                return;
            }
            // Different
            certificateCnMatchRegEx = newCertificateCnMatchRegEx;
            if (!StringUtils.hasText(certificateCnMatchRegEx)) {
                certificateCnMatch = null;
            } else {
                certificateCnMatch = Pattern.compile(certificateCnMatchRegEx);
            }
        }
    }

    /**
     * Test Hook
     *
     * @param regEx
     */
    void checkForRegExTest(final String regEx) {
        certificateCnMatchRefershMs = NEXT_MS_INCREMENT_CHECK + System.currentTimeMillis();
        certificateCnMatchRegEx = regEx;
        if (certificateCnMatchRegEx != null) {
            certificateCnMatch = Pattern.compile(regEx);
        } else {
            certificateCnMatch = null;
        }
    }

    @StroomStartup
    public void init() {
        checkForRegEx();
    }

    @Cacheable(cacheName = "serviceCache", keyGenerator = @KeyGenerator(name = "ListCacheKeyGenerator") )
    public String checkCertificate(final boolean isDn, final String originalCert) {
        checkForRegEx();

        // Use a local copy as could change in the middle of this code
        final Pattern requestCertificateCnMatch = certificateCnMatch;

        if (requestCertificateCnMatch == null) {
            return null;
        }
        if (originalCert == null) {
            LOGGER.warn("doFilter() - No Certificate Provided");
            return "Certificate Required";
        }

        String cert = originalCert;
        if (isDn) {
            cert = CertificateUtil.extractCNFromDN(originalCert);
        }

        final Matcher matcher = requestCertificateCnMatch.matcher(cert);
        if (!matcher.find() || matcher.end() == 0) {
            LOGGER.warn("doFilter() - Certificate CN " + cert + " does not match " + requestCertificateCnMatch.pattern()
                    + ".  Certificate DN " + originalCert);
            return "Certificate Not Allowed";
        }
        LOGGER.debug("doFilter() - Certificate CN " + cert + " OK match " + requestCertificateCnMatch.pattern()
                + ".  Certificate DN " + originalCert);

        return null;
    }
}
