package stroom.pipeline.xsltfunctions;

import stroom.pipeline.cache.GeoIp2DatabaseReaderCacheImpl;
import stroom.pipeline.xml.converter.json.JSONFactoryConfig;
import stroom.pipeline.xml.converter.json.JSONParser;
import stroom.util.NullSafe;
import stroom.util.date.DateUtil;
import stroom.util.shared.Severity;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringReader;
import java.net.InetAddress;
import java.time.Instant;
import javax.inject.Inject;

/**
 * <p>
 * Performs GeoIP lookup of an IP address, using the Maxmind GeoIP2 Java library. A stream containing a Maxmind
 * GeoIP2 database is required.
 * </p>
 * <p>
 * The response is an JSON XML element tree, modelled after the
 * <a href="https://maxmind.github.io/GeoIP2-java/doc/v4.2.0/com.maxmind.geoip2/com/maxmind/geoip2/model/CityResponse.html">CityResponse</a> class.
 * </p>
 */
class LookupGeoIp2 extends StroomExtensionFunctionCall {

    private final GeoIp2DatabaseReaderCacheImpl databaseReaderCache;

    @Inject
    LookupGeoIp2(final GeoIp2DatabaseReaderCacheImpl databaseReaderCache) {
        this.databaseReaderCache = databaseReaderCache;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        Sequence result = EmptyAtomicSequence.getInstance();

        try {
            final boolean ignoreLookupErrors = arguments.length >= 5 &&
                    NullSafe.isTrue(getSafeBoolean(functionName, context, arguments, 4));
            try {
                final InetAddress ipAddress = InetAddress.getByName(
                        getSafeString(functionName, context, arguments, 0));
                final String feedName = getSafeString(functionName, context, arguments, 1);
                final String streamType = getSafeString(functionName, context, arguments, 2);
                final Instant time = DateUtil.parseNormalDateTimeStringToInstant(
                        getSafeString(functionName, context, arguments, 3));

                final DatabaseReader databaseReader = databaseReaderCache.getReader(feedName, streamType, time);
                final CityResponse cityResponse = databaseReader.city(ipAddress);

                result = createSequence(context, cityResponse);
            } catch (final IOException | SAXException | RuntimeException e) {
                log(context, Severity.ERROR, e.getMessage(), e);
            } catch (final GeoIp2Exception e) {
                if (!ignoreLookupErrors) {
                    log(context, Severity.WARNING, e.getMessage(), e);
                }
            }
        } catch (XPathException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return result;
    }

    private Sequence createSequence(final XPathContext context, final CityResponse city)
            throws SAXException, IOException {
        final Configuration configuration = context.getConfiguration();
        final PipelineConfiguration pipe = configuration.makePipelineConfiguration();
        final Builder builder = new TinyBuilder(pipe);

        final ReceivingContentHandler contentHandler = new ReceivingContentHandler();
        contentHandler.setPipelineConfiguration(pipe);
        contentHandler.setReceiver(builder);

        final JSONParser parser = new JSONParser(new JSONFactoryConfig(), false);
        parser.setContentHandler(contentHandler);

        parser.parse(new InputSource(new StringReader(city.toJson())));

        final Sequence sequence = builder.getCurrentRoot();

        // Reset the builder, detaching it from the constructed document
        builder.reset();

        return sequence;
    }
}
