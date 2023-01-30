package tokhttp3;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import org.whispersystems.signalservice.api.util.TlsProxySocketFactory;

/**
 *
 * @author johan
 */
public class OkHttpClient {

    private static final Logger LOG = Logger.getLogger(OkHttpClient.class.getName());

    private HttpClient jClient;

    public OkHttpClient(HttpClient jClient) {
        this.jClient = jClient;
    }

    public MyCall newCall(Request request) {
        return new MyCall(jClient, request);
    }

    public OkHttpClient.Builder newBuilder() {
        return new Builder();
    }

    public WebSocket newWebSocket(Request request, WebSocketListener listener) {
       // HttpClient httpClient = HttpClient.newHttpClient();
        java.net.http.WebSocket.Builder wsBuilder = jClient.newWebSocketBuilder();
        TokWebSocket answer = new TokWebSocket(listener);
        URI uri = request.getUri();
    //    uri = URI.create("wss://signal7.gluonhq.net/time/");
        System.err.println("Create websocket to URI = "+uri);
        CompletableFuture<java.net.http.WebSocket> buildAsync = wsBuilder.buildAsync(uri, new java.net.http.WebSocket.Listener() {
            @Override
            public void onOpen(java.net.http.WebSocket webSocket) {
                try {
                System.err.println("ONOPEN!");
                listener.onOpen(answer, null);
                System.err.println("notified listener1");
                java.net.http.WebSocket.Listener.super.onOpen(webSocket);
                System.err.println("notified listener2");
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }

            @Override
            public CompletionStage<?> onText(java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
                listener.onMessage(answer, data.toString());
                return null;
            }

        });
        System.err.println("building...");
        
        Executors.newCachedThreadPool().submit(() -> {
            try {
            Thread.sleep(500);
                            System.err.println("try to join...");

            java.net.http.WebSocket ws = buildAsync.join();
                System.err.println("yes, got ws = "+ws);
            answer.setJavaWebSocket(ws);
                System.err.println("stored ws on answer");
            } catch (Throwable t) {
                t.printStackTrace();
            }
            return null;
        });
        
//        try {
//            java.net.http.WebSocket ws = buildAsync.join();//.get(5, TimeUnit.SECONDS);
//            System.err.println("building done...");
//            answer.setJavaWebSocket(ws);
//        } catch (Exception ex) {
//            Logger.getLogger(OkHttpClient.class.getName()).log(Level.SEVERE, null, ex);
//        } 
        return answer;
    }

    public static class Builder {

        HttpClient.Builder realBuilder;

        public Builder() {
            realBuilder = HttpClient.newBuilder();
        }

        public Builder connectTimeout(long soTimeoutMillis, TimeUnit timeUnit) {
            System.err.println("SET CT to "+soTimeoutMillis);
            if (timeUnit.equals(TimeUnit.MILLISECONDS)) {
                realBuilder.connectTimeout(Duration.ofMillis(soTimeoutMillis));
            } else if (timeUnit.equals(TimeUnit.SECONDS)) {
                realBuilder.connectTimeout(Duration.ofSeconds(soTimeoutMillis));
            } else {
                System.err.println("UNKNOWN UNIT: "+timeUnit);
            }
            return this;
        }

        public Builder readTimeout(long soTimeoutMillis, TimeUnit timeUnit) {
            return this;
        }

        public OkHttpClient build() {
            HttpClient httpClient = realBuilder.build();
            httpClient.connectTimeout().ifPresent(d -> System.err.println("DURRRR "+d.getSeconds()));
            return new OkHttpClient(httpClient);
        }

        public Builder retryOnConnectionFailure(boolean automaticNetworkRetry) {
            LOG.severe("We don't allow automatic networkretries in tokhttpclient");
            return this;
        }

        public Builder followRedirects(boolean b) {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        public Builder sslSocketFactory(SSLSocketFactory sslSocketFactory, X509TrustManager x509TrustManager) {
            LOG.severe("We don't allow custom sslfactories in tokhttpclient");
            return this;
        }

        public Builder connectionSpecs(List<ConnectionSpec> orElse) {
            LOG.severe("We don't allow custom connection specs in tokhttpclient");
            return this;
        }

        public Builder dns(Dns orElse) {
            LOG.severe("We don't allow custom dns in tokhttpclient");
            return this;
        }

        public Builder socketFactory(TlsProxySocketFactory tlsProxySocketFactory) {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        public Builder addInterceptor(Interceptor interceptor) {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        public Builder connectionPool(ConnectionPool connectionPool) {
            LOG.severe("We don't allow connection pools in tokhttpclient");
            return this;
        }
    }

}
