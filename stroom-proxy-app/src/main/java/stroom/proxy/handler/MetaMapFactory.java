package stroom.proxy.handler;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import stroom.feed.MetaMap;
import stroom.feed.StroomHeaderArguments;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.StringTokenizer;

public class MetaMapFactory {
    public MetaMap create() {
        MetaMap metaMap = new MetaMap();

        HttpServletRequest httpServletRequest = getHttpServletRequest();
        addAllHeaders(httpServletRequest, metaMap);
        addAllQueryString(httpServletRequest, metaMap);

        return metaMap;
    }

    protected HttpServletRequest getHttpServletRequest() {
        HttpServletRequest httpServletRequest = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();
        return httpServletRequest;
    }

    @SuppressWarnings("unchecked")
    private void addAllHeaders(HttpServletRequest httpServletRequest, MetaMap metaMap) {
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String header = headerNames.nextElement();
            metaMap.put(header, httpServletRequest.getHeader(header));
        }
    }

    private void addAllQueryString(HttpServletRequest httpServletRequest, MetaMap metaMap) {
        String queryString = httpServletRequest.getQueryString();
        if (queryString != null) {
            StringTokenizer st = new StringTokenizer(httpServletRequest.getQueryString(), "&");
            while (st.hasMoreTokens()) {
                String pair = st.nextToken();
                int pos = pair.indexOf('=');
                if (pos != -1) {
                    String key = pair.substring(0, pos);
                    String val = pair.substring(pos + 1, pair.length());

                    metaMap.put(key, val);
                }
            }
        }
    }

    public static MetaMap cloneAllowable(final MetaMap in) {
        final MetaMap metaMap = new MetaMap();
        metaMap.putAll(in);
        metaMap.removeAll(StroomHeaderArguments.HEADER_CLONE_EXCLUDE_SET);
        return metaMap;
    }
}
