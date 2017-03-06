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

import com.google.gwt.user.client.rpc.IncompatibleRemoteServiceException;
import com.google.gwt.user.client.rpc.SerializationException;
import com.google.gwt.user.server.rpc.RPC;
import com.google.gwt.user.server.rpc.RPCRequest;
import com.google.gwt.user.server.rpc.RemoteServiceServlet;
import com.google.gwt.user.server.rpc.SerializationPolicy;
import com.google.gwt.user.server.rpc.SerializationPolicyLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.context.ServletContextAware;
import org.springframework.web.servlet.HandlerAdapter;
import org.springframework.web.servlet.ModelAndView;
import stroom.util.thread.ThreadScopeContextHolder;

import javax.annotation.Resource;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;

@Component
public class RemoteServiceHandlerAdapter extends RemoteServiceServlet implements HandlerAdapter, ServletContextAware {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteServiceHandlerAdapter.class);
    private static final long serialVersionUID = -7421136737990135393L;
    private static ThreadLocal<Object> handlerHolder = new ThreadLocal<Object>();
    private transient ServletContext servletContext;

    @Resource
    private transient HttpServletRequestHolder httpServletRequestHolder;

    @Override
    public long getLastModified(final HttpServletRequest request, final Object handler) {
        return -1;
    }

    @Override
    public ModelAndView handle(final HttpServletRequest request, final HttpServletResponse response,
            final Object handler) throws Exception {
        try {
            if (!ThreadScopeContextHolder.contextExists()) {
                throw new IllegalStateException("ThreadScopeContext MUST EXIST");
            }


            httpServletRequestHolder.set(request);
            SessionListListener.setLastRequest(request);

            if (handler instanceof Servlet) {
                final Servlet servlet = (Servlet) handler;
                final ServletConfig config = new DelegatingServletConfig(servlet.getClass().getName(),
                        getServletContext());
                servlet.init(config);
                servlet.service(request, response);
            } else if (handler instanceof HttpRequestHandler) {
                final HttpRequestHandler servlet = (HttpRequestHandler) handler;
                servlet.handleRequest(request, response);
            } else {
                // Store the handler for retrieval in processCall().
                handlerHolder.set(handler);
                doPost(request, response);
            }

        } catch (final Exception ex) {
            LOGGER.error("handle() - {}", request.getRequestURI(), ex);
            throw ex;
        } finally {
            // Make sure this thread no longer references this request as it
            // might be reused for other processing. We also don't want to hold
            // on to this request for longer than necessary.
            httpServletRequestHolder.set(null);
        }

        return null;
    }

    protected Object getCurrentHandler() {
        return handlerHolder.get();
    }

    @Override
    public boolean supports(final Object handler) {
        return true;
    }

    @Override
    public String processCall(final String payload) throws SerializationException {
        // The code below is borrowed from RemoteServiceServlet.processCall,
        // with the following changes:
        // 1) Changed object for decoding and invocation to be the handler
        // (versus the original 'this')
        RPCRequest rpcRequest = null;
        String response = null;

        try {
            final Object handler = getCurrentHandler();

            rpcRequest = RPC.decodeRequest(payload, handler.getClass(), this);
            onAfterRequestDeserialized(rpcRequest);
            response = RPC.invokeAndEncodeResponse(handler, rpcRequest.getMethod(), rpcRequest.getParameters(),
                    rpcRequest.getSerializationPolicy());
        } catch (final IncompatibleRemoteServiceException e) {
            LOGGER.error("An IncompatibleRemoteServiceException was thrown while processing this call.", e);
            if (rpcRequest != null) {
                response = RPC.encodeResponseForFailure(rpcRequest.getMethod(), e, rpcRequest.getSerializationPolicy());
            } else {
                response = RPC.encodeResponseForFailure(null, e);
            }
        } catch (final Throwable e) {
            if (rpcRequest != null) {
                LOGGER.error("processCall() - rpcMethod=" + rpcRequest.getMethod() + ", rpcRequest=" + rpcRequest + " ",
                        e);
                response = RPC.encodeResponseForFailure(rpcRequest.getMethod(), e, rpcRequest.getSerializationPolicy());
            } else {
                LOGGER.error("processCall() rpcRequest=null ", e);
                response = RPC.encodeResponseForFailure(null, e);
            }
        } finally {
            // Make sure this thread no longer references this request as it
            // might be reused for other processing. We also don't want to hold
            // on to this request for longer than necessary.
            httpServletRequestHolder.set(null);
        }

        return response;
    }

    @Override
    public ServletContext getServletContext() {
        return servletContext;
    }

    @Override
    public void setServletContext(final ServletContext servletContext) {
        this.servletContext = servletContext;

        try {
            final ServletConfig config = new DelegatingServletConfig(getClass().getName(), getServletContext());
            init(config);
            // codeServerHost = getCodeServerHost();
            // codeServerPort = getCodeServerPort();
        } catch (final ServletException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    @Override
    protected SerializationPolicy doGetSerializationPolicy(
            HttpServletRequest request, String moduleBaseURL, String strongName) {
        String serializationPolicyFilePath = getSerializationPolicyFilePath(request, moduleBaseURL, strongName);

//        try {
//            List<String> paths1 = getResourceFiles("/");
//            LOGGER.info("There are {} path1", paths1);
//            paths1.forEach(path -> LOGGER.info("Path1 in web app: {}", paths1));
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
        Set<String> paths = getServletContext().getResourcePaths("./");


        LOGGER.info("There are {} paths", paths);
        paths.forEach(path -> LOGGER.info("Path in web app: {}", path));

        try(InputStream is = getServletContext().getResourceAsStream(String.format("%s", serializationPolicyFilePath))){
            if(is == null){
                try(InputStream jarIs = getServletContext().getResourceAsStream(String.format("ui/%s", serializationPolicyFilePath))){
                    return SerializationPolicyLoader.loadFromStream(is, null);
                }
            }
            return SerializationPolicyLoader.loadFromStream(is, null);
        } catch (IOException | ParseException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO this is adapted/copied from RequestServiceServlet
    private String getSerializationPolicyFilePath(HttpServletRequest request, String moduleBaseURL, String strongName) {
        // The request can tell you the path of the web app relative to the
        // container root.
        String contextPath = request.getContextPath();

        String modulePath = null;
        if (moduleBaseURL != null) {
            try {
                modulePath = new URL(moduleBaseURL).getPath();
            } catch (MalformedURLException ex) {
                // log the information, we will default
                log("Malformed moduleBaseURL: " + moduleBaseURL, ex);
            }
        }

        SerializationPolicy serializationPolicy = null;

    /*
     * Check that the module path must be in the same web app as the servlet
     * itself. If you need to implement a scheme different than this, override
     * this method.
     */
        if (modulePath == null || !modulePath.startsWith(contextPath)) {
            String message = "ERROR: The module path requested, "
                    + modulePath
                    + ", is not in the same web application as this servlet, "
                    + contextPath
                    + ".  Your module may not be properly configured or your client and server code maybe out of date.";
            log(message);
            throw new RuntimeException(message);
        } else {
            // Strip off the context path from the module base URL. It should be a
            // strict prefix.
            String contextRelativePath = modulePath.substring(contextPath.length());

            String serializationPolicyFilePath = SerializationPolicyLoader.getSerializationPolicyFileName(contextRelativePath
                    + strongName);
            return serializationPolicyFilePath;
        }
    }

    private List<String> getResourceFiles(String path ) throws IOException {
        List<String> filenames = new ArrayList<>();

        try(
                InputStream in = getResourceAsStream( path );
                BufferedReader br = new BufferedReader( new InputStreamReader( in ) ) ) {
            String resource;

            while( (resource = br.readLine()) != null ) {
                filenames.add( resource );
            }
        }

        return filenames;
    }

    private InputStream getResourceAsStream( String resource ) {
        final InputStream in
                = getContextClassLoader().getResourceAsStream( resource );

        return in == null ? getClass().getResourceAsStream( resource ) : in;
    }

    private ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }

    private class DelegatingServletConfig implements ServletConfig {
        private final String servletName;
        private final ServletContext servletContext;

        public DelegatingServletConfig(final String servletName, final ServletContext servletContext) {
            this.servletName = servletName;
            this.servletContext = servletContext;
        }

        @Override
        public String getServletName() {
            return servletName;
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
        }

        @Override
        public String getInitParameter(final String paramName) {
            return null;
        }

        @Override
        public Enumeration getInitParameterNames() {
            return null;
        }
    }
}
