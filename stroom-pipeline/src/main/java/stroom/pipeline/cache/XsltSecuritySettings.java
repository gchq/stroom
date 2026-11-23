/*
 * Copyright 2026 Crown Copyright
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

package stroom.pipeline.cache;

import stroom.pipeline.filter.XsltConfig;
import stroom.util.xml.SaxonPolicy;
import stroom.util.xml.SaxonSecurity;

import net.sf.saxon.s9api.Processor;

/// The XSLT security settings an XSLT was compiled with, so a pooled XSLT can be recompiled when they change.
///
/// @param secureMode Whether pipeline XSLT is secured.
/// @param policy     What pipeline XSLT may read when secured.
record XsltSecuritySettings(boolean secureMode, SaxonPolicy policy) {

    /// Makes the settings for an XSLT configuration. Unusable allowed URI prefixes are warned about here.
    ///
    /// The policy is ignored when secure mode is off, so changing the allowed URI prefixes then doesn't
    /// make pooled XSLT recompile.
    ///
    /// @param xsltConfig The XSLT configuration.
    /// @return The security settings in it.
    static XsltSecuritySettings from(final XsltConfig xsltConfig) {
        if (!xsltConfig.isSecureMode()) {
            return new XsltSecuritySettings(false, SaxonPolicy.LOCKED);
        }
        return new XsltSecuritySettings(true, SaxonPolicy.allowing(xsltConfig.getAllowedUriPrefixes()));
    }

    /// @return A new Saxon processor for these settings, secured unless an administrator has turned secure
    /// mode off.
    Processor newProcessor() {
        return secureMode
                ? SaxonSecurity.newProcessor(policy)
                : SaxonSecurity.newUnsecuredProcessor();
    }
}
