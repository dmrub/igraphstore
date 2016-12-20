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
import javax.net.ssl.SSLContext;
import org.apache.http.HttpHost;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.DnsResolver;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.protocol.HttpContext;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.jena.riot.web.HttpOp;

/**
 *
 * @author rubinste
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

    static class MyConnectionSocketFactory extends PlainConnectionSocketFactory {

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

        public MySSLConnectionSocketFactory(final SSLContext sslContext) {
            // You may need this verifier if target site's certificate is not secure
            super(sslContext, ALLOW_ALL_HOSTNAME_VERIFIER);
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

    public static CloseableHttpClient createHttpClient() {
        String socksProxyHost = System.getProperty("socksProxyHost");
        String socksProxyPort = System.getProperty("socksProxyPort");

        if (socksProxyHost != null && socksProxyPort != null) {
            System.out.format("Detected SOCKS configuration:%nsocksProxyHost=%s%nsocksProxyPort=%s%nConfigure proxy support", socksProxyHost, socksProxyPort);

            Registry<ConnectionSocketFactory> reg = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", new MyConnectionSocketFactory())
                    .register("https", new MySSLConnectionSocketFactory(SSLContexts.createSystemDefault())).build();
            PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(reg, new FakeDnsResolver());

            return HttpClients.custom().setConnectionManager(cm).build();
        } else {
            return HttpClients.createDefault();
        }
    }

    public static void initHttpClient() {
        if (httpClient == null) {
            httpClient = createHttpClient();
        }

        HttpOp.setDefaultHttpClient(httpClient);
    }

}
