package com.yahoo.vespa.athenz.identity;

import com.yahoo.vespa.athenz.api.AthenzService;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

public class IdentitySslSocketFactory extends SSLSocketFactory implements ServiceIdentityProvider.Listener {
    private volatile SSLSocketFactory currentSslSocketFactory;

    public IdentitySslSocketFactory(SSLContext sslContext) {
        onCredentialsUpdate(sslContext, null);
    }

    @Override
    public void onCredentialsUpdate(SSLContext sslContext, AthenzService identity) {
        currentSslSocketFactory = sslContext.getSocketFactory();
    }


    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return currentSslSocketFactory.createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return currentSslSocketFactory.createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return currentSslSocketFactory.createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return currentSslSocketFactory.createSocket(address, port, localAddress, localPort);
    }

    @Override
    public Socket createSocket(Socket socket, String host, int port, boolean autoClose) throws IOException {
        return currentSslSocketFactory.createSocket(socket, host, port, autoClose);
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return currentSslSocketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return currentSslSocketFactory.getSupportedCipherSuites();
    }
}
