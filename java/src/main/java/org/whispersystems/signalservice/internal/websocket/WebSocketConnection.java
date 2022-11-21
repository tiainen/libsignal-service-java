package org.whispersystems.signalservice.internal.websocket;

import com.google.protobuf.InvalidProtocolBufferException;

import org.signal.libsignal.protocol.logging.Log;
import org.signal.libsignal.protocol.util.Pair;
import org.whispersystems.signalservice.api.push.TrustStore;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.util.SleepTimer;
import org.whispersystems.signalservice.api.util.Tls12SocketFactory;
import org.whispersystems.signalservice.api.util.TlsProxySocketFactory;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
import org.whispersystems.signalservice.internal.configuration.SignalProxy;
import org.whispersystems.signalservice.internal.util.BlacklistingTrustManager;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;
import org.whispersystems.signalservice.internal.util.concurrent.SettableFuture;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Logger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.ConnectionSpec;
import okhttp3.Dns;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

import static org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketMessage;
import static org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
import static org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketResponseMessage;

public class WebSocketConnection extends WebSocketListener {

    private static final String TAG = WebSocketConnection.class.getSimpleName();
    private static final int KEEPALIVE_TIMEOUT_SECONDS = 55;

    private final LinkedList<WebSocketRequestMessage> incomingRequests = new LinkedList<>();
    private final Map<Long, OutgoingRequest> outgoingRequests = new HashMap<>();

    private final String wsUri;
    private final TrustStore trustStore;
    private final Optional<CredentialsProvider> credentialsProvider;
    private final String signalAgent;
    private final ConnectivityListener listener;
    private final SleepTimer sleepTimer;
    private final List<Interceptor> interceptors;
    private final Optional<Dns> dns;
    private final Optional<SignalProxy> signalProxy;
    private final boolean allowStories;

    private WebSocket client;
    private KeepAliveSender keepAliveSender;
    private int attempts;
    private boolean connected;
    private Consumer callback;
    
    private static final Logger LOG = Logger.getLogger(WebSocketConnection.class.getName());


    public WebSocketConnection(String httpUri,
            TrustStore trustStore,
            Optional<CredentialsProvider> credentialsProvider,
            String signalAgent,
            ConnectivityListener listener,
            SleepTimer timer,
            List<Interceptor> interceptors,
            Optional<Dns> dns,
            Optional<SignalProxy> signalProxy,
            Consumer callback, 
            boolean allowStories) {
        this(httpUri, "", trustStore, credentialsProvider, signalAgent, listener, timer, interceptors, dns, signalProxy, callback, allowStories);

    }

    public WebSocketConnection(String httpUri,
            String path,
            TrustStore trustStore,
            Optional<CredentialsProvider> credentialsProvider,
            String signalAgent,
            ConnectivityListener listener,
            SleepTimer timer,
            List<Interceptor> interceptors,
            Optional<Dns> dns,
            Optional<SignalProxy> signalProxy,
            Consumer callback,
            boolean allowStories) {
        this.trustStore = trustStore;
        this.credentialsProvider = credentialsProvider;
        this.signalAgent = signalAgent;
        this.listener = listener;
        this.sleepTimer = timer;
        this.interceptors = interceptors;
        this.dns = dns;
        this.signalProxy = signalProxy;
        this.attempts = 0;
        this.connected = false;
        this.callback = callback;
        this.allowStories = allowStories;

        String uri = httpUri.replace("https://", "wss://").replace("http://", "ws://");
        if (credentialsProvider.isPresent()) {
            this.wsUri = uri + "/v1/websocket/" + path + "?login=%s&password=%s";
        } else {
            this.wsUri = uri + "/v1/websocket/" + path;
        }
        LOG.info("WebSocketConnection created with url = " + this.wsUri + ": " + this);

    }

    public synchronized void connect() {
        LOG.info("connect() for " + this);

        if (client == null) {
            String filledUri;

            if (credentialsProvider.isPresent()) {
                String identifier = credentialsProvider.get().getAci() != null ? credentialsProvider.get().getDeviceUuid() : credentialsProvider.get().getE164();
                filledUri = String.format(wsUri, identifier, credentialsProvider.get().getPassword());
            } else {
                filledUri = wsUri;
            }
            Pair<SSLSocketFactory, X509TrustManager> socketFactory = createTlsSocketFactory(trustStore);

            OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder()
                    .sslSocketFactory(new Tls12SocketFactory(socketFactory.first()), socketFactory.second())
                    .connectionSpecs(Util.immutableList(ConnectionSpec.RESTRICTED_TLS))
                    .readTimeout(KEEPALIVE_TIMEOUT_SECONDS + 10, TimeUnit.SECONDS)
                    .dns(dns.orElse(Dns.SYSTEM))
                    .connectTimeout(KEEPALIVE_TIMEOUT_SECONDS + 10, TimeUnit.SECONDS);

            for (Interceptor interceptor : interceptors) {
                clientBuilder.addInterceptor(interceptor);
            }

            if (signalProxy.isPresent()) {
                clientBuilder.socketFactory(new TlsProxySocketFactory(signalProxy.get().getHost(), signalProxy.get().getPort(), dns));
            }

            OkHttpClient okHttpClient = clientBuilder.build();

            Request.Builder requestBuilder = new Request.Builder().url(filledUri);

            if (signalAgent != null) {
                requestBuilder.addHeader("X-Signal-Agent", signalAgent);
            }
            requestBuilder.addHeader("X-Signal-Receive-Stories", allowStories ? "true" : "false");

            if (listener != null) {
                listener.onConnecting();
            }

            this.connected = false;
            Request request = requestBuilder.build();
            Log.d(TAG, "[WSC] now connecting websocket for request " + request);
            this.client = okHttpClient.newWebSocket(request, this);
        }
    }

    public synchronized void disconnect() {
        Log.i(TAG, "disconnect() asked by user for " + this);

        if (client != null) {
            client.close(1000, "OK");
            client = null;
            connected = false;
        }

        if (keepAliveSender != null) {
            keepAliveSender.shutdown();
            keepAliveSender = null;
        }

        notifyAll();
    }

    public synchronized WebSocketRequestMessage readRequest(long timeoutMillis)
            throws TimeoutException, IOException {
        if (client == null) {
            throw new IOException("Connection closed!");
        }

        long startTime = System.currentTimeMillis();

        while (client != null && incomingRequests.isEmpty() && elapsedTime(startTime) < timeoutMillis) {
            long et = elapsedTime(startTime);
            Util.wait(this, Math.max(1, timeoutMillis - et));
        }

        if (incomingRequests.isEmpty() && client == null) {
            throw new IOException("Connection closed!");
        } else if (incomingRequests.isEmpty()) {
            throw new TimeoutException("Timeout exceeded");
        } else {
            LOG.fine("readrequest will handover " + Objects.hashCode(incomingRequests.getFirst()));
            return incomingRequests.removeFirst();
        }
    }

    public synchronized ListenableFuture<WebsocketResponse> sendRequest(WebSocketRequestMessage request) throws IOException {
        if (client == null || !connected) {
            throw new IOException("No connection!");
        }

        WebSocketMessage message = WebSocketMessage.newBuilder()
                .setType(WebSocketMessage.Type.REQUEST)
                .setRequest(request)
                .build();
        SettableFuture<WebsocketResponse> future = new SettableFuture<>();
        LOG.fine("putting outgoingrequest " + request.getId() + " on queue");
        outgoingRequests.put(request.getId(), new OutgoingRequest(future, System.currentTimeMillis()));
        LOG.fine("outgoingRequests now has " + outgoingRequests.size()+" elements.");
        if (!client.send(ByteString.of(message.toByteArray()))) {
            throw new IOException("Write failed!");
        }

        return future;
    }

    public synchronized void sendResponse(WebSocketResponseMessage response) throws IOException {
        if (client == null) {
            throw new IOException("Connection closed!");
        }

        WebSocketMessage message = WebSocketMessage.newBuilder()
                .setType(WebSocketMessage.Type.RESPONSE)
                .setResponse(response)
                .build();

        if (!client.send(ByteString.of(message.toByteArray()))) {
            throw new IOException("Write failed!");
        }
    }

    private synchronized void sendKeepAlive() throws IOException {
        if (keepAliveSender != null && client != null) {
            byte[] message = WebSocketMessage.newBuilder()
                    .setType(WebSocketMessage.Type.REQUEST)
                    .setRequest(WebSocketRequestMessage.newBuilder()
                            .setId(System.currentTimeMillis())
                            .setPath("/v1/keepalive")
                            .setVerb("GET")
                            .build()).build()
                    .toByteArray();
            if (!client.send(ByteString.of(message))) {
                throw new IOException("Write failed!");
            }
        }
    }

    @Override
    public synchronized void onOpen(WebSocket webSocket, Response response) {
        LOG.info("Establishing WebSocket connection "+webSocket+" called in thread "+Thread.currentThread());
        Log.d(TAG, "[WSC] onOpen for " + this+", response = "+response);
        if (client != null && keepAliveSender == null) {
            LOG.info("onOpen() connected, client = " + client);
            attempts = 0;
            connected = true;
            keepAliveSender = new KeepAliveSender();
            LOG.info("keepalive thread = " + keepAliveSender);
            keepAliveSender.start();

            if (listener != null) {
                listener.onConnected();
            }
            if (this.callback != null) {
                this.callback.accept(this);
            }
        }
    }

    @Override
    public synchronized void onMessage(WebSocket webSocket, ByteString payload) {
        long mid = 0;
        try {
            WebSocketMessage message = WebSocketMessage.parseFrom(payload.toByteArray());
            LOG.info("Websocket gets message of type " + message.getType()+
                    ", with websocket queuesize = "+webSocket.queueSize());
            mid = Objects.hashCode(message.getRequest());
            if (message.getType().getNumber() == WebSocketMessage.Type.REQUEST_VALUE) {
                WebSocketRequestMessage req = message.getRequest();
                LOG.info("Received message: got request "+req.getVerb()+" "+req.getPath());
                incomingRequests.add(req);
                LOG.info("Message with id "+mid+" added to incomingRequestsqueue, queuesize = "+incomingRequests.size());
            } else if (message.getType().getNumber() == WebSocketMessage.Type.RESPONSE_VALUE) {
                OutgoingRequest listener = outgoingRequests.get(message.getResponse().getId());
                LOG.info("incoming message is response for request with id " + message.getResponse().getId() + " and listener = " + listener);
                if (listener != null) {
                    listener.getResponseFuture().set(
                            new WebsocketResponse(message.getResponse().getStatus(),
                            new String(message.getResponse().getBody().toByteArray()),
                            message.getResponse().getHeadersList()));
                }
            }

            notifyAll();
        } catch (InvalidProtocolBufferException e) {
            Log.w(TAG, e);
        }
        LOG.finer(Thread.currentThread()+" handled onMessage "+mid);
    }

    @Override
    public synchronized void onClosed(WebSocket webSocket, int code, String reason) {
        Log.i(TAG, "onClosed() for " + this + " on webSocket "+webSocket+" with reason = " + reason);
        this.connected = false;

        Iterator<Map.Entry<Long, OutgoingRequest>> iterator = outgoingRequests.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<Long, OutgoingRequest> entry = iterator.next();
            entry.getValue().getResponseFuture().setException(new IOException("Closed: " + code + ", " + reason));
            iterator.remove();
        }

        if (keepAliveSender != null) {
            keepAliveSender.shutdown();
            keepAliveSender = null;
        }

        if (listener != null) {
            listener.onDisconnected();
        }

        Util.wait(this, Math.min(++attempts * 500, TimeUnit.MINUTES.toMillis(15)));

        if (client != null) {
            client.close(1000, "OK");
            client = null;
            connected = false;
            connect();
        }

        notifyAll();
    }

    @Override
    public synchronized void onFailure(WebSocket webSocket, Throwable t, Response response) {
        LOG.warning("[WSC] onFailure for " + this+" with throwable "+(t == null ? "empty" : t.getMessage()));

        Log.w(TAG, "onFailure()", t);

        if (response != null && (response.code() == 401 || response.code() == 403)) {
            if (listener != null) {
                listener.onAuthenticationFailure();
            }
        } else if (listener != null) {
            boolean shouldRetryConnection = listener.onGenericFailure(response, t);
            if (!shouldRetryConnection) {
                Log.w(TAG, "Experienced a failure, and the listener indicated we should not retry the connection. Disconnecting.");
                disconnect();
            }
        }

        if (client != null) {
            onClosed(webSocket, 1000, "OK");
        }
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        System.err.println("[WSC] 2 onMessage, text = " + text);
        Log.d(TAG, "onMessage(text)");
    }

    @Override
    public synchronized void onClosing(WebSocket webSocket, int code, String reason) {
        Log.i(TAG, "onClosing() for " + this+" and webSocket "+webSocket+" with code = " + code+" and reason = "+reason);
        webSocket.close(1000, "OK");
    }

    private long elapsedTime(long startTime) {
        return System.currentTimeMillis() - startTime;
    }

    private Pair<SSLSocketFactory, X509TrustManager> createTlsSocketFactory(TrustStore trustStore) {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            TrustManager[] trustManagers = BlacklistingTrustManager.createFor(trustStore);
            context.init(null, trustManagers, null);

            return new Pair<>(context.getSocketFactory(), (X509TrustManager) trustManagers[0]);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            throw new AssertionError(e);
        }
    }

    private class KeepAliveSender extends Thread {

        private AtomicBoolean stop = new AtomicBoolean(false);

        public void run() {
            while (!stop.get()) {
                try {
                    sleepTimer.sleep(TimeUnit.SECONDS.toMillis(KEEPALIVE_TIMEOUT_SECONDS));

                    LOG.finest("Sending keep alive for " + this);
                    sendKeepAlive();
                } catch (Throwable e) {
                    Log.d(TAG, "FAILED Sending keep alive for " + this);
                    Log.w(TAG, e);
                }
            }
            Log.w(TAG, "No more keepalives for " + this);
        }

        public void shutdown() {
            Log.d(TAG, "Requesting to stop keep alive for " + this);
            stop.set(true);
        }
    }

    private static class OutgoingRequest {

        private final SettableFuture<WebsocketResponse> responseFuture;
        private final long startTimestamp;

        private OutgoingRequest(SettableFuture<WebsocketResponse> future, long startTimestamp) {
            this.responseFuture = future;
            this.startTimestamp = startTimestamp;
        }

        SettableFuture<WebsocketResponse> getResponseFuture() {
            return responseFuture;
        }

        long getStartTimestamp() {
            return startTimestamp;
        }
    }
}
