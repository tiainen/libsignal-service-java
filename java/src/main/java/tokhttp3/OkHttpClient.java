package tokhttp3;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.X509TrustManager;
import okio.ByteString;
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

    @Deprecated
    public MyCall newCall(Request request) {
        LOG.info("OLD NEWCALL, we're removing this");
        Thread.dumpStack();
        return new MyCall(jClient, request);
    }

    public MyCall newCall(HttpRequest request, byte[] raw) {
        MyCall answer = new MyCall(jClient, request, raw);
        LOG.info("NewCall created "+answer);
        return answer;
    }

    public OkHttpClient.Builder newBuilder() {
        return new Builder();
    }

    public WebSocket newWebSocket(Request request, WebSocketListener listener) {
        boolean useGrpc = Boolean.getBoolean("wave.grpc");
        if (useGrpc) {
            GrpcWebSocket gws;
            try {
                LOG.info("Try grpcwebsocket");
                gws = new GrpcWebSocket(listener);
                Map<String, String> headers = new HashMap<>();
                request.getHttpRequest().headers().map().forEach((String k, List<String> v) -> {
                    v.stream().forEach(val -> headers.put(k, val));
                });
                LOG.info("Try opening grpcwebsocket");
                gws.open(request.getUri().toString(), headers, listener);
                LOG.info("Opened grpcwebsocket");
                return gws;
            } catch (IOException ex) {
                Logger.getLogger(OkHttpClient.class.getName()).log(Level.SEVERE, null, ex);
                LOG.severe("Creating websocket using grpc failed, fallback to normal");
            }
        }
        java.net.http.WebSocket.Builder wsBuilder = jClient.newWebSocketBuilder();
        TokWebSocket answer = new TokWebSocket(listener);
        URI uri = request.getUri();
        LOG.info("Create websocket to URI = " + uri.getHost());
        request.getHttpRequest().headers().map().forEach((String k, List<String> v) -> {
            v.stream().forEach(val -> wsBuilder.header(k, val));
        });
        CompletableFuture<java.net.http.WebSocket> buildAsync = wsBuilder.buildAsync(uri, new java.net.http.WebSocket.Listener() {
            @Override
            public void onOpen(java.net.http.WebSocket webSocket) {
                try {
                    LOG.info("java.net ws is opened");
                    listener.onOpen(answer, null);
                    LOG.info("notified listener1");
                    java.net.http.WebSocket.Listener.super.onOpen(webSocket);
                    System.err.println("notified listener2");
                } catch (Throwable e) {
                    e.printStackTrace();
                    LOG.log(Level.SEVERE, "error in onopen", e);
                }
            }

            @Override
            public void onError(java.net.http.WebSocket webSocket, Throwable error) {
                LOG.log(Level.SEVERE, "ERROR IN WEBSOCKET!", error);
                error.printStackTrace();
            }

            @Override
            public CompletionStage<?> onClose(java.net.http.WebSocket webSocket, int statusCode, String reason) {
                LOG.severe("WEBSOCKET CLOSED with code " + statusCode + " and reason " + reason);
                Thread.dumpStack();
                return null;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();

            @Override
            public CompletionStage<?> onBinary(java.net.http.WebSocket webSocket, ByteBuffer data, boolean last) {
                LOG.info("Websocket receives binary data on " + Thread.currentThread() + ", last = " + last + ", limit = " + data.limit() + ", remaining = " + data.remaining() + ", cap = " + data.capacity());
                webSocket.request(1);
                byte[] buff = new byte[data.remaining()];
                data.get(buff);
                try {
                    baos.write(buff);
                    if (last) {
                        byte[] completed = baos.toByteArray();
                        baos = new ByteArrayOutputStream();
                        System.err.println("total size = " + completed.length);
                        listener.onMessage(answer, ByteString.of(completed));
                    }
                } catch (Throwable t) {
                    t.printStackTrace();
                    LOG.log(Level.SEVERE, "error in receiving ws data", t);
                }
                return null;
            }

            @Override
            public CompletionStage<?> onText(java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
                LOG.info("Websocket receives text");
                webSocket.request(1);

                try {
                    listener.onMessage(answer, data.toString());
                } catch (Throwable t) {
                    t.printStackTrace();
                    LOG.log(Level.SEVERE, "error in receiving ws data", t);

                }
                return null;
            }

        });
        LOG.info("Building websocket...");

        Executors.newCachedThreadPool().submit(() -> {
            try {
                LOG.info("try to join...");
                java.net.http.WebSocket ws = buildAsync.join();
                LOG.info("yes, got ws = " + ws.hashCode());
                answer.setJavaWebSocket(ws);
            } catch (Throwable t) {
                t.printStackTrace();
                LOG.log(Level.SEVERE, "error in receiving ws data", t);

            }
            return null;
        });

        return answer;
    }

    public static class Builder {

        HttpClient.Builder realBuilder;

        public Builder() {
            realBuilder = HttpClient.newBuilder();
        }

        public Builder connectTimeout(long soTimeoutMillis, TimeUnit timeUnit) {
            if (timeUnit.equals(TimeUnit.MILLISECONDS)) {
                realBuilder.connectTimeout(Duration.ofMillis(soTimeoutMillis));
            } else if (timeUnit.equals(TimeUnit.SECONDS)) {
                realBuilder.connectTimeout(Duration.ofSeconds(soTimeoutMillis));
            } else {
                LOG.severe("UNKNOWN UNIT: " + timeUnit);
            }
            return this;
        }

        public Builder readTimeout(long soTimeoutMillis, TimeUnit timeUnit) {
            return this;
        }

        public OkHttpClient build() {
            HttpClient httpClient = realBuilder.build();
            return new OkHttpClient(httpClient);
        }

        public Builder retryOnConnectionFailure(boolean automaticNetworkRetry) {
            return this;
        }

        public Builder followRedirects(boolean b) {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        public Builder sslSocketFactory(SSLSocketFactory sslSocketFactory, X509TrustManager x509TrustManager) {
            return this;
        }

        public Builder connectionSpecs(List<ConnectionSpec> orElse) {
            return this;
        }

        public Builder dns(Dns orElse) {
            return this;
        }

        public Builder socketFactory(TlsProxySocketFactory tlsProxySocketFactory) {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        public Builder addInterceptor(Interceptor interceptor) {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        public Builder connectionPool(ConnectionPool connectionPool) {
            return this;
        }
    }

}
