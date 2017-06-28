///*
// * Copyright 2016 Crown Copyright
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *     http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//
//package stroom.servlet;
//
//import org.springframework.stereotype.Component;
//import stroom.util.cert.CertificateUtil;
//
//import javax.annotation.Resource;
//import javax.servlet.Filter;
//import javax.servlet.FilterChain;
//import javax.servlet.FilterConfig;
//import javax.servlet.ServletException;
//import javax.servlet.ServletRequest;
//import javax.servlet.ServletResponse;
//import javax.servlet.http.HttpServletRequest;
//import javax.servlet.http.HttpServletResponse;
//import java.io.IOException;
//
///**
// * Basic filter to check that a certificate is allowed for a URL.
// */
//@Component
//public class ClusterCallCertificateRequiredFilter implements Filter {
//    @Resource
//    private ClusterCallCertificateRequiredCache clusterCallCertificateRequiredCache;
//
//    @Override
//    public void destroy() {
//    }
//
//    @Override
//    public void doFilter(final ServletRequest request, final ServletResponse response, final FilterChain chain)
//            throws IOException, ServletException {
//        final String certDn = CertificateUtil.extractCertificateDN((HttpServletRequest) request);
//
//        // This is a cache call
//        final String msg = clusterCallCertificateRequiredCache.checkCertificate(true, certDn);
//
//        if (msg != null) {
//            ((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED, msg);
//        } else {
//            // OK - no msg
//            chain.doFilter(request, response);
//        }
//    }
//
//    @Override
//    public void init(final FilterConfig arg0) throws ServletException {
//    }
//}
