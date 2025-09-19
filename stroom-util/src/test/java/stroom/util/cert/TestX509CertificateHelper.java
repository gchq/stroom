package stroom.util.cert;

import stroom.test.common.ProjectPathUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.eclipse.jetty.util.resource.PathResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.util.security.CertificateUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateParsingException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.Stream;
import javax.security.auth.x500.X500Principal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * For how to set up OCSP responder, see:
 * <a href="https://github.com/xperseguers/ocsp-responder/blob/master/Documentation/CertificateAuthority.md">
 * OCSP Responder
 * </a>
 */
@ExtendWith(MockitoExtension.class)
class TestX509CertificateHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestX509CertificateHelper.class);
    private static final Path BASE_PATH = ProjectPathUtil.getRepoRoot()
            .getParent()
            .resolve("stroom-resources")
            .resolve("dev-resources")
            .resolve("certs");

    @Mock
    private CertVerificationConfig mockReceiveDataConfig;

    // client.pem.crt
    private static final String CLIENT_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIEIjCCAwqgAwIBAgIUHKL7iCNnqA26iXwvM8dbYmR0KGcwDQYJKoZIhvcNAQEN
            BQAwfTELMAkGA1UEBhMCVUsxFjAUBgNVBAcMDVRlc3QgTG9jYWxpdHkxGjAYBgNV
            BAoMEVRlc3QgT3JnYW5pemF0aW9uMSEwHwYDVQQLDBhUZXN0IE9yZ2FuaXphdGlv
            bmFsIFVuaXQxFzAVBgNVBAMMDlN0cm9vbSBSb290IENBMB4XDTI1MDkwMTEzNTEy
            NVoXDTM5MDUxMTEzNTEyNVowZDELMAkGA1UEBhMCVUsxFjAUBgNVBAcMDVRlc3Qg
            TG9jYWxpdHkxGjAYBgNVBAoMEVRlc3QgT3JnYW5pemF0aW9uMSEwHwYDVQQDDBhB
            IFRlc3QgQ2xpZW50ICh0ZXN0dXNlcikwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAw
            ggEKAoIBAQCtfgAkAipcledVoZ54X1sgFvtOmegvbpAYD27mTIUxQGHtnRtei0tu
            S+NFQQB5DMWaw89IibEui0DN6xeI2Y5tlHQ9fQm/4YhYJwPsGazJUzP++woftuKn
            Mzxm2C+YOivQquIkxXmvF7HWNDimHCruFdu8VzStKxqtVTUosXxqoelAMF6sMAA8
            g/oepe4bs2dBv1+xaGreu4p/z5c1e8qKUPvdS5j+qfcvgXBgDs0NM63U0qUWKEmw
            DtIGr9z4yYqwhngwrQizj1yWGatWVniBcRV32Mjn5wBUHPCvFwkfYE8BJ9uHHRxz
            Z+wRHOC5MjQFxZCOD26OCnZ9p/AGvyCPAgMBAAGjgbIwga8wHQYDVR0OBBYEFPT2
            NFzu92f+zfzcCElbuLLNk3HrMB8GA1UdIwQYMBaAFAnpBsyEqd6N37ddzjtF74Al
            GPIsMAkGA1UdEwQCMAAwCwYDVR0PBAQDAgWgMCcGA1UdJQQgMB4GCCsGAQUFBwMC
            BggrBgEFBQcDAwYIKwYBBQUHAwQwLAYJYIZIAYb4QgENBB8WHU9wZW5TU0wgR2Vu
            ZXJhdGVkIENlcnRpZmljYXRlMA0GCSqGSIb3DQEBDQUAA4IBAQBf4uVvem3mOCq4
            DPu2Y3Os2cxKerMeZEl1HFFMtwjA5j3fot+ousRKQ9B5UrmSYRGoaZ5UXUjKf0vH
            gAVFW4Zz+WoChMJ3oFedLr10hcLhpwI9ODg430ogVmcLdVP+f/T2grtpVLEmhDuh
            zc+FmZ5+FzoehG+JePSckAHjbw/tx7zUQhxck82LbBrhOCipyMTfLGh7tfCf4cAd
            Umys1kHZh1Tz68lnoTc7kBlaYnUIXLu/2NCmzBgGKNHZBvSrPpxI2NztvIjEwecJ
            TGVFaFNVWBJ2L0FFef3+MoRiW0zktm8j9eM2nMmVvYCtXfh3MjEim1bAURUZk3PW
            92UMf0Nf
            -----END CERTIFICATE-----""";

    // These certs were created by createCerts.sh in stroom-resources repo
    // client2.pem.crt
    private static final String CLIENT2_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIELDCCAxSgAwIBAgIUV8n5kxqN5t/HzxkHwnuuPabKbRcwDQYJKoZIhvcNAQEN
            BQAwgYUxCzAJBgNVBAYTAlVLMRYwFAYDVQQHDA1UZXN0IExvY2FsaXR5MRowGAYD
            VQQKDBFUZXN0IE9yZ2FuaXphdGlvbjEhMB8GA1UECwwYVGVzdCBPcmdhbml6YXRp
            b25hbCBVbml0MR8wHQYDVQQDDBZTdHJvb20gSW50ZXJtZWRpYXRlIENBMB4XDTI1
            MDkwMTEzNTEyNVoXDTM5MDUxMTEzNTEyNVowZTELMAkGA1UEBhMCVUsxFjAUBgNV
            BAcMDVRlc3QgTG9jYWxpdHkxGjAYBgNVBAoMEVRlc3QgT3JnYW5pemF0aW9uMSIw
            IAYDVQQDDBlUZXN0IENsaWVudCAyICh0ZXN0dXNlcjIpMIIBIjANBgkqhkiG9w0B
            AQEFAAOCAQ8AMIIBCgKCAQEAyUd9hW9VYf8/Hges0sH7LdqroJwN4vjc9GRDDsyC
            ZCHgWBN+FIrW/jCO5rBiA373ZUmbOwp5u1uwlsTJQM4nklXvbuiJdAWEBmfEazJe
            6TKv9VGYq0+0wvWM776BdsKoAzNHUVGcpimr97AJmEC0FncQwnfrILY7vBck/XuO
            DWAjmlFpUIgmF1Hg9r17LZmaJTXlywFrZbE2NLzq3PZRqUPDiRuLfSGTbzJIPeQi
            AiKKoNaOsyrKySgyMAcT+IKbvaHewronvILsBvY0ZB6Md/B7XNYETAQ+q4jVn5eq
            7LrWG1ilNdC6YambC/nKvJ1ltphOrPC7KOeJ4ByN7dtXkQIDAQABo4GyMIGvMB0G
            A1UdDgQWBBT2DcGIWqlKpaovKV+tnPOjk84+zjAfBgNVHSMEGDAWgBTdf497WUcz
            z/E0OcOGKxYlgSP5kTAJBgNVHRMEAjAAMAsGA1UdDwQEAwIFoDAnBgNVHSUEIDAe
            BggrBgEFBQcDAgYIKwYBBQUHAwMGCCsGAQUFBwMEMCwGCWCGSAGG+EIBDQQfFh1P
            cGVuU1NMIEdlbmVyYXRlZCBDZXJ0aWZpY2F0ZTANBgkqhkiG9w0BAQ0FAAOCAQEA
            VsGEuFZ9ARX9AKzLyeqzIrwFDenwnv8VMV6oslxaJnsUs65u8SUkCahNa8tf4Hw9
            gJPrqdY24aENMfFPXhGnjqoVvgbzHE5AaWer3HCelCpux8PFQnrT3xRhO4jkrWnK
            ILkYgK7KsvOVpTjsTXT2ZuhyybP1H1PCsSWjpXSvc42woWlaYZ/AOAdT5YQ2UKNL
            GE9a2tbzzjANalykgl6srHSMiC0sbepJkGPgZYlRSA4T7DjJnM1JrSsSk0iBhCbN
            T729fNnwE8r5tE9uw4wk9SzfTAitvLmaLfXF1kA6UHGdDeBI1qav3ZK44uiz8jqj
            EUSDltGOw4jBLCCBxHMldQ==
            -----END CERTIFICATE-----""";
    // intermediate_ca.pem.crt
    private static final String INTERMEDIATE_CA_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIEHjCCAwagAwIBAgIUZsHIQsddgBqptL2Kb6qel/pO3b8wDQYJKoZIhvcNAQEN
            BQAwfTELMAkGA1UEBhMCVUsxFjAUBgNVBAcMDVRlc3QgTG9jYWxpdHkxGjAYBgNV
            BAoMEVRlc3QgT3JnYW5pemF0aW9uMSEwHwYDVQQLDBhUZXN0IE9yZ2FuaXphdGlv
            bmFsIFVuaXQxFzAVBgNVBAMMDlN0cm9vbSBSb290IENBMB4XDTI1MDkwMTEzNTEy
            NVoXDTM5MDUxMTEzNTEyNVowgYUxCzAJBgNVBAYTAlVLMRYwFAYDVQQHDA1UZXN0
            IExvY2FsaXR5MRowGAYDVQQKDBFUZXN0IE9yZ2FuaXphdGlvbjEhMB8GA1UECwwY
            VGVzdCBPcmdhbml6YXRpb25hbCBVbml0MR8wHQYDVQQDDBZTdHJvb20gSW50ZXJt
            ZWRpYXRlIENBMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEArRLM63Zk
            sE/sQjHCR3rYGRP03ULqNyvcNlKbXFPDDrG9rcQgjoIP9V+OQyiUDjrmlquTF1VL
            i9AtG55KvywZZZweqNEkSS4oOykTV8i0t7OCVtr2f9NeZ7vgVT2K5XOtzaBOAiQJ
            vJld09lgyI+CR8B6UgAmzrbqo450CeL1pfZL7vUf5gWWa3hu6B12SIOqNjm4thtf
            barLL+DOIjoSlsYKEFIywrEzcxsyw4HE9L7JuAomD+ZuR958d6ofY2lDYe1k+7nE
            KGj9wM4JewDUNSQdevRkqcNJ535vxgKpIBoY0QlyNfNVUwBzmZBVJQpmxq42hdei
            ar8FlD4Qs/NzGQIDAQABo4GMMIGJMB0GA1UdDgQWBBTdf497WUczz/E0OcOGKxYl
            gSP5kTAfBgNVHSMEGDAWgBQJ6QbMhKnejd+3Xc47Re+AJRjyLDAMBgNVHRMEBTAD
            AQH/MAsGA1UdDwQEAwIBpjAsBglghkgBhvhCAQ0EHxYdT3BlblNTTCBHZW5lcmF0
            ZWQgQ2VydGlmaWNhdGUwDQYJKoZIhvcNAQENBQADggEBAGkYRr0rM4ZqBF/dk+y2
            ah/UEJ60C3i3ujHnXkFwiyUUOoLmm4aeWoNZ1yHCTBslpn7ztHoC4HJbfP2QX4Mv
            7KiF7ZixkL9kkFekpg/TFMKtwRiNWj46oroZXmOVIkK/K+AkNgfnog03VGRHOz2P
            66oRQLNiO7p0Wrskmq1VOAiZRCGO/oKeNb4kD6LjScGHhBRZgdpy9tfkP/OEENMf
            yvLSJQPF9Ta4VC2QYqs9D8x1qQ20bYRzI1XlcPl6ZuhAGHsGfUTmxCXJ6EOsB529
            sm5SkwsavlMGopnZXyFsGtZQ1l+b/54baDWqU0D6U30BwUkK/MHxi+Bi3vWvikKF
            68E=
            -----END CERTIFICATE-----""";
    // ca.pem.crt
    private static final String CA_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIEFTCCAv2gAwIBAgIUIhara15lhCAnCM3FLv3Vvwk500cwDQYJKoZIhvcNAQEN
            BQAwfTELMAkGA1UEBhMCVUsxFjAUBgNVBAcMDVRlc3QgTG9jYWxpdHkxGjAYBgNV
            BAoMEVRlc3QgT3JnYW5pemF0aW9uMSEwHwYDVQQLDBhUZXN0IE9yZ2FuaXphdGlv
            bmFsIFVuaXQxFzAVBgNVBAMMDlN0cm9vbSBSb290IENBMB4XDTI1MDkwMTEzNTEy
            NFoXDTM5MDUxMTEzNTEyNFowfTELMAkGA1UEBhMCVUsxFjAUBgNVBAcMDVRlc3Qg
            TG9jYWxpdHkxGjAYBgNVBAoMEVRlc3QgT3JnYW5pemF0aW9uMSEwHwYDVQQLDBhU
            ZXN0IE9yZ2FuaXphdGlvbmFsIFVuaXQxFzAVBgNVBAMMDlN0cm9vbSBSb290IENB
            MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAnYqDRjWOKh2Z9FTK2yo8
            jKQV99cAgdGkPtqOl7IVV/PEBw38QC3cQ/cAKkAfUHTdXe5dQl3bUb8fY+GJwP3C
            MUCIdKHVc17iXww9i+F1VAAwcfGeqrumU4LzzgIfhYIGm6O2KFRNWol7YJ3oP0zg
            esdtMszcDrmcS3htsZew7pKN7xA378iXtAs8Yc5jixpleh/glg7RF2ZpKSaBMTU1
            P+4uKHKCDRYDocDQKNMFRUSkW2HFcN+PfmecGVnBQKSW1MRO/6hBaBLif7z7kqmt
            4u47HevHDKGIkYZXCnOEvQEhTe6gyGKguLOj4dXwwQhdJX7JDWXhDXIqeStW60TB
            YQIDAQABo4GMMIGJMB0GA1UdDgQWBBQJ6QbMhKnejd+3Xc47Re+AJRjyLDAfBgNV
            HSMEGDAWgBQJ6QbMhKnejd+3Xc47Re+AJRjyLDAMBgNVHRMEBTADAQH/MAsGA1Ud
            DwQEAwIBpjAsBglghkgBhvhCAQ0EHxYdT3BlblNTTCBHZW5lcmF0ZWQgQ2VydGlm
            aWNhdGUwDQYJKoZIhvcNAQENBQADggEBAA2R5zDqHfKBUuzD4EcnQESyB3vOg8Jf
            mjd6joC34Zj5joprNduUAfYAfa9IZBOAWnJRwypFg9QLBUfDMyElDlYj9ODdxQ6C
            Kxq1E8tybaxchjo4eC/RByA0A29svtI+YoiUA5XqGbvmqiFp0fY8/UD2JaRLIVoq
            dGw9/3aFGIav93nGGeva0ucACucWTDYWt4B8PbhRHILH9uDttEO9hrBv0amom0Su
            WOPjpGUIZEDWyCLpQ399t27eYZ+0fhi6+1GGdFbKdedSJdvNXfisTeFv5gg80Aoi
            uZyITQSQqYe4VH/lakok/d2z9rC3zMOMYoBLBiE+uQeG+VGab6i+RYA=
            -----END CERTIFICATE-----""";
    private static final String CLIENT_CERT_CHAIN = String.join("\n", CLIENT_CERT, CA_CERT);
    private static final String CLIENT2_CERT_CHAIN = String.join("\n", CLIENT2_CERT, INTERMEDIATE_CA_CERT);
    private static final String CA_CERT_BUNDLE = String.join("\n", INTERMEDIATE_CA_CERT, CA_CERT);

    @Test
    void testExtractCertificate_clientChain() {

        final String urlEncodedBundle = URLEncoder.encode(CLIENT2_CERT_CHAIN, StandardCharsets.UTF_8);
        final List<X509Certificate> certs = new X509CertificateHelper()
                .parseX509Certificates(urlEncodedBundle, EncodingType.URL_ENCODED);

        assertThat(certs)
                .hasSize(2);
        assertThat(certs.stream()
                .map(X509Certificate::getSubjectX500Principal)
                .map(X500Principal::getName)
                .map(Object::toString)
                .toList())
                .containsExactly(
                        "CN=Test Client 2 (testuser2),O=Test Organization,L=Test Locality,C=UK",
                        "CN=Stroom Intermediate CA,OU=Test Organizational Unit,O=Test " +
                        "Organization,L=Test Locality,C=UK");

        for (final X509Certificate cert : certs) {
            LOGGER.info("{}", cert.getSerialNumber());
        }
    }

    @Test
    void info() throws CertificateParsingException {
        final List<X509Certificate> allCerts = Stream.of(CLIENT2_CERT, INTERMEDIATE_CA_CERT, CA_CERT)
                .map(str -> URLEncoder.encode(str, StandardCharsets.UTF_8))
                .flatMap(encoded -> new X509CertificateHelper()
                        .parseX509Certificates(encoded, EncodingType.URL_ENCODED).stream())
                .toList();

        for (final X509Certificate cert : allCerts) {
            LOGGER.info("""
                            DN: {}
                              Serial: {}
                              Issuer DN: {}
                              Issuer unique ID: {}
                              Issuer alt names: {}""",
                    cert.getSubjectX500Principal().getName(),
                    cert.getSerialNumber(),
                    cert.getIssuerX500Principal(),
                    cert.getIssuerUniqueID(),
                    cert.getIssuerAlternativeNames());
        }
    }

    @Test
    void testExtractCertificate_caBundle() {
        final String urlEncodedBundle = URLEncoder.encode(CA_CERT_BUNDLE, StandardCharsets.UTF_8);

        final List<X509Certificate> certs = new X509CertificateHelper()
                .parseX509Certificates(urlEncodedBundle, EncodingType.URL_ENCODED);

        assertThat(certs)
                .hasSize(2);
        assertThat(certs.stream()
                .map(X509Certificate::getSubjectX500Principal)
                .map(X500Principal::getName)
                .map(Object::toString)
                .toList())
                .containsExactly(
                        "CN=Stroom Intermediate CA,OU=Test Organizational Unit,O=Test " +
                        "Organization,L=Test Locality,C=UK",
                        "CN=Stroom Root CA,OU=Test Organizational Unit,O=Test Organization,L=Test Locality,C=UK");
    }

    @Test
    void testValidate() {
        final Path trustStorePath = ProjectPathUtil.getRepoRoot()
                .getParent()
                .resolve("stroom-resources")
                .resolve("dev-resources")
                .resolve("certs")
                .resolve("chain")
                .resolve("ca_bundle.jks");
        final KeyStore trustStore = createTrustStore(trustStorePath);

        final String urlEncodedBundle = URLEncoder.encode(CLIENT2_CERT_CHAIN, StandardCharsets.UTF_8);
        final List<X509Certificate> certs = new X509CertificateHelper()
                .parseX509Certificates(urlEncodedBundle, EncodingType.URL_ENCODED);

        Mockito.when(mockReceiveDataConfig.isValidateClientCertificateExpiry())
                .thenReturn(true);

        new X509CertificateHelper()
                .validateCertificates(certs, trustStore, mockReceiveDataConfig);

    }

    @Test
    void testValidate2() throws NoSuchAlgorithmException {
        final Path trustStorePath = ProjectPathUtil.getRepoRoot()
                .getParent()
                .resolve("stroom-resources")
                .resolve("dev-resources")
                .resolve("certs")
                .resolve("certificate-authority")
                .resolve("ca.jks");
        final KeyStore trustStore = createTrustStore(trustStorePath);

        final String urlEncodedBundle = URLEncoder.encode(CLIENT_CERT_CHAIN, StandardCharsets.UTF_8);
        final List<X509Certificate> certs = X509CertificateHelper.parseX509Certificates(
                urlEncodedBundle, EncodingType.URL_ENCODED);

        assertThat(certs)
                .hasSize(2);

        Mockito.when(mockReceiveDataConfig.isValidateClientCertificateExpiry())
                .thenReturn(true);

        new X509CertificateHelper()
                .validateCertificates(certs, trustStore, mockReceiveDataConfig);

    }

    @Test
    void testValidate3() throws CertificateException {
        final Path trustStorePath = ProjectPathUtil.getRepoRoot()
                .getParent()
                .resolve("stroom-resources")
                .resolve("dev-resources")
                .resolve("certs")
                .resolve("chain")
                .resolve("ca_bundle.jks");
        final KeyStore trustStore = createTrustStore(trustStorePath);

        final X509Certificate clientCert = getClientCert();

//        final String urlEncodedBundle = URLEncoder.encode(CLIENT_CERT, StandardCharsets.UTF_8);
//        final List<X509Certificate> certs = X509CertificateHelper.parseX509Certificates(
//                urlEncodedBundle, EncodingType.URL_ENCODED);
//
//        assertThat(certs)
//                .hasSize(1);

        Mockito.when(mockReceiveDataConfig.isValidateClientCertificateExpiry())
                .thenReturn(true);

        new X509CertificateHelper()
                .validateCertificates(List.of(clientCert), trustStore, mockReceiveDataConfig);

//        new X509CertificateHelper()
//                .validateCertificates();

    }

    private X509Certificate getClientCert() {
        return getCertsFromFile(BASE_PATH.resolve("client")
                .resolve("client.pem.crt"))
                .getFirst();
    }

    private X509Certificate getClient2Cert() {
        return getCertsFromFile(BASE_PATH.resolve("chain")
                .resolve("client2.pem.crt"))
                .getFirst();
    }

    private List<X509Certificate> getClientChainCerts() {
        return getCertsFromFile(BASE_PATH.resolve("chain")
                .resolve("client_bundle.pem.crt"));
    }

    private List<X509Certificate> getCABundleCerts() {
        return getCertsFromFile(BASE_PATH.resolve("chain")
                .resolve("ca_bundle.pem.crt"));
    }

    private List<X509Certificate> getCertsFromFile(final Path path) {
        if (!Files.isRegularFile(path)) {
            throw new RuntimeException(LogUtil.message("{} is not a regular file or does not exist",
                    path.toAbsolutePath()));
        }

        try {
            final String str = Files.readString(path);
            return X509CertificateHelper.parseX509Certificates(str, EncodingType.PLAIN_TEXT);
        } catch (final IOException e) {
            throw new RuntimeException(LogUtil.message("Error reading file {} - ",
                    path.toAbsolutePath(), LogUtil.exceptionMessage(e)), e);
        }
    }

    private static KeyStore createTrustStore(final Path trustStorePath) {
        try (final Resource trustStoreResource = PathResource.newResource(trustStorePath)) {
            return CertificateUtils.getKeyStore(
                    trustStoreResource,
                    "JKS",
                    null,
                    "password");
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
