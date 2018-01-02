/*
 * This file is part of IGraphStore. It is subject to the license terms in
 * the LICENSE file found in the top-level directory of this distribution.
 * You may not use this file except in compliance with the License.
 */
package de.dfki.resc28.igraphstore.util;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import org.apache.http.HttpHost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.jena.riot.web.HttpOp;
import org.apache.http.ssl.SSLContextBuilder;

/**
 *
 * @author Dmitri Rubinstein
 */
public class ProxyConfigurator {

    private static CloseableHttpClient httpClient;

    static class FakeDnsResolver implements DnsResolver {

        @Override
        public InetAddress[] resolve(String host) throws UnknownHostException {
            // Return some fake DNS record for every request, we won't be using it
            return new InetAddress[]{InetAddress.getByAddress(new byte[]{1, 1, 1, 1})};
        }
    }

    static class MyPlainConnectionSocketFactory extends PlainConnectionSocketFactory {

        @SuppressWarnings("FieldNameHidesFieldInSuperclass")
        public static final MyPlainConnectionSocketFactory INSTANCE = new MyPlainConnectionSocketFactory();

        public static MyPlainConnectionSocketFactory getSocketFactory() {
            return INSTANCE;
        }

        @Override
        public Socket createSocket(final HttpContext context) throws IOException {
            InetSocketAddress socksaddr = (InetSocketAddress) context.getAttribute("socks.address");
            if (socksaddr != null) {
                Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksaddr);
                return new Socket(proxy);
            } else {
                return new Socket();
            }
        }

        @Override
        public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress,
                InetSocketAddress localAddress, HttpContext context) throws IOException {
            // Convert address to unresolved
            InetSocketAddress unresolvedRemote = InetSocketAddress
                    .createUnresolved(host.getHostName(), remoteAddress.getPort());
            return super.connectSocket(connectTimeout, socket, host, unresolvedRemote, localAddress, context);
        }
    }

    static class MySSLConnectionSocketFactory extends SSLConnectionSocketFactory {

        public MySSLConnectionSocketFactory(SSLContext sslContext, HostnameVerifier hostnameVerifier) {
            super(sslContext, hostnameVerifier);
        }

        @Override
        public Socket createSocket(final HttpContext context) throws IOException {
            InetSocketAddress socksaddr = (InetSocketAddress) context.getAttribute("socks.address");
            if (socksaddr != null) {
                Proxy proxy = new Proxy(Proxy.Type.SOCKS, socksaddr);
                return new Socket(proxy);
            } else {
                return new Socket();
            }
        }

        @Override
        public Socket connectSocket(int connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress,
                InetSocketAddress localAddress, HttpContext context) throws IOException {
            // Convert address to unresolved
            InetSocketAddress unresolvedRemote = InetSocketAddress
                    .createUnresolved(host.getHostName(), remoteAddress.getPort());
            return super.connectSocket(connectTimeout, socket, host, unresolvedRemote, localAddress, context);
        }
    }

    // code based on http://literatejava.com/networks/ignore-ssl-certificate-errors-apache-httpclient-4-4/
    public static CloseableHttpClient createHttpClient(boolean insecureSSL, boolean useSocks) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        if (!insecureSSL && !useSocks) {
            return HttpClients.createDefault();
        }

        final SSLContextBuilder sslB = SSLContextBuilder.create();
        final HostnameVerifier hostnameVerifier;
        if (insecureSSL) {
            // don't check Hostnames, either.
            hostnameVerifier = NoopHostnameVerifier.INSTANCE;

            // setup a Trust Strategy that allows all certificates.
            //
            sslB.loadTrustMaterial(null, new TrustStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
                    return true;
                }
            });
        } else {
            hostnameVerifier = SSLConnectionSocketFactory.getDefaultHostnameVerifier();
        }

        final SSLContext sslContext = sslB.build();

        final SSLConnectionSocketFactory sslSocketFactory;
        final PlainConnectionSocketFactory plainSocketFactory;

        // here's the special part:
        //      -- need to create an SSL Socket Factory, to use our weakened "trust strategy";
        //      -- and create a Registry, to register it.
        //
        if (useSocks) {
            plainSocketFactory = MyPlainConnectionSocketFactory.getSocketFactory();
            sslSocketFactory = new MySSLConnectionSocketFactory(sslContext, hostnameVerifier);
        } else {
            plainSocketFactory = PlainConnectionSocketFactory.getSocketFactory();
            sslSocketFactory = new SSLConnectionSocketFactory(sslContext, hostnameVerifier);
        }

        Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", plainSocketFactory)
                .register("https", sslSocketFactory)
                .build();

        // now, we create connection-manager using our Registry.
        //      -- allows multi-threaded use
        PoolingHttpClientConnectionManager connMgr
                = new PoolingHttpClientConnectionManager(
                        socketFactoryRegistry,
                        useSocks ? new FakeDnsResolver() : null);

        // finally, build the HttpClient;
        //      -- done!
        return HttpClientBuilder.create().setSSLContext(sslContext)
                .setConnectionManager(connMgr).build();
    }

    public static CloseableHttpClient createHttpClient() {
        String socksProxyHost = System.getProperty("socksProxyHost");
        String socksProxyPort = System.getProperty("socksProxyPort");
        String insecureSSLValue = System.getProperty("ssl.insecure");
        boolean insecureSSL = ("true".equalsIgnoreCase(insecureSSLValue)
                || "1".equals(insecureSSLValue));
        boolean useSocks = socksProxyHost != null && socksProxyPort != null;

        if (insecureSSL) {
            System.err.format("WARNING: Enabled insecure SSL/TLS%n");
        }

        if (useSocks) {
            System.out.format("Detected SOCKS configuration:%nsocksProxyHost=%s%nsocksProxyPort=%s%nConfigure proxy support", socksProxyHost, socksProxyPort);
        }

        try {
            return createHttpClient(insecureSSL, useSocks);
        } catch (NoSuchAlgorithmException ex) {
            Logger.getLogger(ProxyConfigurator.class.getName()).log(
                    Level.SEVERE, "Could not create HTTP client with insecure SSL", ex);
        } catch (KeyStoreException ex) {
            Logger.getLogger(ProxyConfigurator.class.getName()).log(
                    Level.SEVERE, "Could not create HTTP client with insecure SSL", ex);
        } catch (KeyManagementException ex) {
            Logger.getLogger(ProxyConfigurator.class.getName()).log(
                    Level.SEVERE, "Could not create HTTP client with insecure SSL", ex);
        }

        return HttpClients.createDefault();
    }

    public static void initHttpClient() {
        if (httpClient == null) {
            httpClient = createHttpClient();
        }

        HttpOp.setDefaultHttpClient(httpClient);
    }

}
