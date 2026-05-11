/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.dropwizard.common.sysinfo;


import stroom.util.collections.CollectionUtil;
import stroom.util.collections.CollectionUtil.DuplicateMode;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.IsAdminServlet;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Unauthenticated;
import stroom.util.sysinfo.HasSystemInfo;
import stroom.util.sysinfo.HasSystemInfo.NamedParamCombination;
import stroom.util.sysinfo.HasSystemInfo.ParamInfo;
import stroom.util.sysinfo.SystemInfoResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Unauthenticated
public class SystemInfoAdminServlet extends HttpServlet implements IsAdminServlet {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SystemInfoAdminServlet.class);

    private static final Set<String> PATH_SPECS = Set.of("/sysinfo");
    private static final String DISPLAY_NAME = "System Info Servlet";
    private static final String PARAM_NAME_PROVIDER = "provider";
    private static final String PARAM_NAME_PRETTY = "pretty";

    private final Map<String, HasSystemInfo> supliersMap;

    @Inject
    public SystemInfoAdminServlet(final Set<HasSystemInfo> systemInfoSuppliers) {
        this.supliersMap = CollectionUtil.mapBy(
                HasSystemInfo::getSystemInfoName,
                DuplicateMode.THROW,
                systemInfoSuppliers);
    }

    @Override
    public Set<String> getPathSpecs() {
        return PATH_SPECS;
    }

    @Override
    public String getDisplayName() {
        return DISPLAY_NAME;
    }

    @Override
    protected void doGet(final HttpServletRequest req, final HttpServletResponse resp)
            throws ServletException, IOException {
        final String providerName = req.getParameter(PARAM_NAME_PROVIDER);

        final Set<String> names = getProviderNames();
        try {
            if (NullSafe.isNonBlankString(providerName)) {
                if (names.contains(providerName)) {
                    displayInfo(req, resp, providerName);
                } else {
                    displayError(req, resp, "Unknown name '" + providerName + "'");
                }
            } else {
                displayNamesList(req, resp, names);
            }
        } catch (final Exception e) {
            displayError(req, resp, e.getMessage());
        }
    }

    private void displayNamesList(final HttpServletRequest req,
                                  final HttpServletResponse resp,
                                  final Set<String> providerNames) throws IOException {

        if (NullSafe.hasItems(providerNames)) {
            final Writer writer = resp.getWriter();
            writeHtmlHeader(writer);
            writer.write("<h1>System Info Providers</h1>");
            writer.write("<ul>");
            writeNewLine(writer);
            for (final String provider : providerNames.stream().sorted().toList()) {
                writer.write("<li><a href=\"./sysinfo?provider="
                             + provider + "&pretty=true\">"
                             + provider + "</a></li>");
                writeNewLine(writer);

                final List<ParamInfo> paramInfoList = NullSafe.list(getParamInfo(provider));
                if (!paramInfoList.isEmpty()) {
                    writer.write("<p>Parameter details:</p>");
                    writeNewLine(writer);
                    writer.write("<ul>");
                    writeNewLine(writer);
                    for (final ParamInfo paramInfo : paramInfoList) {
                        writer.write("<li>");
                        writer.write("<code>" + paramInfo.getName() + "</code> (");
                        writer.write((paramInfo.isMandatory()
                                ? "Mandatory"
                                : "Optional"));
                        writer.write(") - " + paramInfo.getDescription());
                        writer.write("</li>");
                        writeNewLine(writer);
                    }
                    writer.write("</ul>");
                    writeNewLine(writer);
                }

                final List<NamedParamCombination> namedParamCombinations = NullSafe.list(
                        getNamedParamCombinations(provider));
                if (!namedParamCombinations.isEmpty()) {
                    writer.write("<p>Parameter combinations:</p>");
                    writeNewLine(writer);
                    writer.write("<ul>");
                    writeNewLine(writer);
                    for (final NamedParamCombination namedParamCombination : namedParamCombinations) {
                        final String combinationName = namedParamCombination.getName();
                        final String paramStr = namedParamCombination.getParams()
                                .entrySet()
                                .stream()
                                .map(entry ->
                                        entry.getKey()
                                        + "="
                                        + URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8))
                                .collect(Collectors.joining("&"));

                        final String url = req.getRequestURI() + "?"
                                           + "provider=" + provider
                                           + "&pretty=true"
                                           + "&" + paramStr;

                        writer.write("<li><a href=\"" + url + "\">"
                                     + combinationName
                                     + "</a> (<code>"
                                     + url + "</code>)</li>");
                        writeNewLine(writer);
                    }
                    writer.write("</ul>");
                    writeNewLine(writer);
                }
            }
            writer.write("</ul>");
            writeNewLine(writer);
            writeHtmlFooter(writer);
            writer.close();
        }
    }

    private static void writeHtmlHeader(final Writer writer) throws IOException {
        writer.write("""
                <!DOCTYPE html>
                <html>
                  <head>
                    <title>System Info Providers</title>
                    <style>
                      body {
                        font-family: arial, tahoma, verdana;
                      }
                      li {
                        line-height: 1.75em;
                      }
                      p {
                        margin-top: 0.5em;
                        margin-bottom: 0.5em;
                      }
                    </style>
                  </head>
                  <body>""");
    }

    private static void writeHtmlFooter(final Writer writer) throws IOException {
        writer.write("""
                  </body>
                </html>""");
    }

    private static void writeNewLine(final Writer writer) throws IOException {
        writer.write("\n");
    }

    private void displayInfo(final HttpServletRequest req,
                             final HttpServletResponse resp,
                             final String providerName)
            throws IOException {
        final Set<String> paramNames = getParamNames(providerName);

        final Optional<SystemInfoResult> systemInfoResult;
        if (NullSafe.hasItems(paramNames)) {
            final Map<String, String> params = new HashMap<>(paramNames.size());
            paramNames.forEach(paramName -> {
                final String value = req.getParameter(paramName);
                params.put(paramName, value);
            });
            systemInfoResult = get(providerName, params);
        } else {
            systemInfoResult = get(providerName);
        }

        if (systemInfoResult.isPresent()) {
            final SystemInfoResult result = systemInfoResult.get();
            try {
                final String json = getJson(req, result);
                resp.setContentType("application/json");
                resp.getWriter().write(json);
            } catch (final JsonProcessingException e) {
                displayError(req, resp, "Error outputting system info - " + e.getMessage());
            }
        }
    }

    private void displayError(final HttpServletRequest req,
                              final HttpServletResponse resp,
                              final String error)
            throws IOException {
        final PrintWriter writer = resp.getWriter();
        writeHtmlHeader(writer);
        writer.write("ERROR: " + error);
        writeHtmlFooter(writer);
    }

    private Set<String> getProviderNames() {
        return supliersMap.keySet();
    }

    public List<ParamInfo> getParamInfo(final String providerName) {
        final HasSystemInfo systemInfoSupplier = getProvider(providerName);
        return NullSafe.stream(systemInfoSupplier.getParamInfo())
                .sorted(Comparator.comparing(ParamInfo::getName))
                .toList();
    }

    private @NonNull HasSystemInfo getProvider(final String providerName) {
        return Objects.requireNonNull(
                supliersMap.get(providerName),
                () -> LogUtil.message("Unknown system info provider name [{}]", providerName));
    }

    public List<NamedParamCombination> getNamedParamCombinations(final String providerName) {
        final HasSystemInfo systemInfoSupplier = getProvider(providerName);
        return NullSafe.stream(systemInfoSupplier.getNamedParamCombinations())
                .sorted(Comparator.comparing(NamedParamCombination::getName))
                .toList();
    }

    public Set<String> getParamNames(final String providerName) {
        return NullSafe.stream(getParamInfo(providerName))
                .map(ParamInfo::getName)
                .collect(Collectors.toSet());
    }

    private Optional<SystemInfoResult> get(final String providerName) {
        return get(providerName, Collections.emptyMap());
    }

    private Optional<SystemInfoResult> get(final String providerName, final Map<String, String> params) {
        // We should have a user in context as this is coming from an authenticated rest api
        final HasSystemInfo systemInfoSupplier = supliersMap.get(providerName);
        try {
            return NullSafe.getAsOptional(
                    systemInfoSupplier,
                    supplier -> supplier.getSystemInfo(params));
        } catch (final Exception e) {
            LOGGER.error("Error getting system info for {} with params {}: {}",
                    providerName, params, e.getMessage(), e);
            throw e;
        }
    }

    private String getJson(final HttpServletRequest req, final SystemInfoResult systemInfoResult)
            throws JsonProcessingException {
        final boolean prettyPrint = Boolean.parseBoolean(req.getParameter(PARAM_NAME_PRETTY));

        final ObjectMapper mapper;
        if (prettyPrint) {
            mapper = JsonUtil.getMapper();
        } else {
            mapper = JsonUtil.getNoIndentMapper();
        }
        return mapper.writeValueAsString(systemInfoResult);
    }
}
