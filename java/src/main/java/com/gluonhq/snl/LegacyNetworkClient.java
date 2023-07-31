package com.gluonhq.snl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
import org.whispersystems.signalservice.internal.configuration.SignalUrl;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketMessage;

/**
 *
 * @author johan
 */
public class LegacyNetworkClient extends NetworkClient {

    private static final Logger LOG = Logger.getLogger(LegacyNetworkClient.class.getName());
    private WebSocket webSocket;
    final HttpClient httpClient;
    KeepAliveSender keepAliveSender;
    private static final int KEEPALIVE_TIMEOUT_SECONDS = 55;

    public LegacyNetworkClient(SignalUrl url, Optional<CredentialsProvider> cp, String signalAgent, Optional<ConnectivityListener> connectivityListener, boolean allowStories) {
        super(url, cp, signalAgent, connectivityListener, allowStories);
        this.httpClient = buildClient();
    }

    private HttpClient buildClient() {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder();
        HttpClient answer = clientBuilder.build();
        return answer;
    }

    @Override
    void implCreateWebSocket(String baseUrl) throws IOException {
        WebSocket.Builder wsBuilder = this.httpClient.newWebSocketBuilder();
        wsBuilder.header("X-Signal-Agent", signalAgent);
        wsBuilder.header("X-Signal-Receive-Stories", allowStories ? "true" : "false");
        URI uri = null;
        try {
            LOG.info("CREATEWS to " + baseUrl);
            uri = new URI(baseUrl);
        } catch (URISyntaxException ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new IOException("Can not create websocket to wrong formatted url", ex);
        }
        if (uri == null) {
            throw new IOException("Can not create websocket to unexisting url");
        }
        WebSocket.Listener myListener = new MyWebsocketListener();
        CompletableFuture<WebSocket> webSocketProcess = wsBuilder.buildAsync(uri, myListener);

        CountDownLatch cdl = new CountDownLatch(1);
        Executors.newCachedThreadPool().submit(() -> {
            try {
                LOG.info("Joining ws...");
                this.webSocket = webSocketProcess.join();
                LOG.info("Done joining ws");
                cdl.countDown();
            } catch (Throwable t) {
                t.printStackTrace();
            }
        });
        try {
            boolean res = cdl.await(10, TimeUnit.SECONDS);
            if (!res) {
                LOG.severe("Failed to reconnect!");
            }
        } catch (InterruptedException ex) {
            LOG.warning("Interrupted while waiting for websocket connection");
            LOG.log(Level.SEVERE, null, ex);
        }
        if (this.webSocket == null) {
            throw new IOException("Could not create a websocket");
        }

    }

    @Override
    protected CompletableFuture<Response> implAsyncSendRequest(HttpRequest request, byte[] raw) throws IOException {
        CompletableFuture<Response> response;
        LOG.info("Send request, not using kwik");
        response = CompletableFuture.completedFuture(getDirectResponse(request));
        LOG.info("Got response, not using kwik");

        return response;
    }

    @Override
    void sendToStream(WebSocketMessage msg, OutgoingPushMessageList list) throws IOException {
        byte[] payload = msg.toByteArray();
        this.webSocket.sendBinary(ByteBuffer.wrap(payload), true);
    }

    private Response getDirectResponse(HttpRequest request) throws IOException {
        HttpResponse httpResponse;
        try {
            LOG.info("Invoke send on httpClient " + this.httpClient);
            httpResponse = this.httpClient.send(request, createBodyHandler());
            LOG.info("Did invoke send on httpClient");
        } catch (InterruptedException ex) {
            LOG.log(Level.SEVERE, "Error sending using httpClient " + this.httpClient, ex);
            throw new IOException(ex);
        }
        return new Response(httpResponse);
    }

    void implShutdown() {
        if (this.keepAliveSender != null) {
            this.keepAliveSender.shutdownKeepAlive();
        }
        if (this.webSocket != null) {
            this.webSocket.abort();
        }
    }

    private synchronized CompletableFuture sendKeepAlive() throws IOException {
        WebSocketMessage message = WebSocketProtos.WebSocketMessage.newBuilder()
                .setType(WebSocketProtos.WebSocketMessage.Type.REQUEST)
                .setRequest(WebSocketProtos.WebSocketRequestMessage.newBuilder()
                        .setId(System.currentTimeMillis())
                        .setPath("/v1/keepalive")
                        .setVerb("GET")
                        .build()).build();
        LOG.info("KEEPALIVE: " + message);
        CompletableFuture fut = CompletableFuture.runAsync(() -> {
            try {
                sendToStream(message, null);
            } catch (Exception ex) {
                Logger.getLogger(NetworkClient.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        return fut;
    }

    class MyWebsocketListener implements WebSocket.Listener {

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            LOG.log(Level.WARNING, "ERROR IN WEBSOCKET, do we have connectivityListener? " + connectivityListener + ", err = " + error);
            connectivityListener.ifPresent(cl -> cl.onError());
            //    reCreateWebSocket();
            //    error.printStackTrace();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            LOG.info("Websocket closed with statusCode " + statusCode + " and reason " + reason + ". Do we have a cl? " + connectivityListener);
            connectivityListener.ifPresent(cl -> cl.onDisconnected());
            return null;
        }
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        @Override
        public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
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
                    rawByteQueue.put(completed);
                    //     listener.onMessage(answer, ByteString.of(completed));
                }
            } catch (Throwable t) {
                t.printStackTrace();
                LOG.log(Level.SEVERE, "error in receiving ws data", t);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            LOG.info("Websocket receives text");
            webSocket.request(1);

            try {
                rawByteQueue.put(data.toString().getBytes());
                //   listener.onMessage(answer, data.toString());
            } catch (Throwable t) {
                t.printStackTrace();
                LOG.log(Level.SEVERE, "error in receiving ws data", t);

            }
            return null;

        }

        @Override
        public void onOpen(WebSocket webSocket) {
            try {
                LOG.info("java.net ws is opened");
                //     listener.onOpen(answer, null);
                LOG.info("notified listener1");
                java.net.http.WebSocket.Listener.super.onOpen(webSocket);
                System.err.println("notified listener2");
                LegacyNetworkClient.this.keepAliveSender = new LegacyNetworkClient.KeepAliveSender();
                keepAliveSender.start();
            } catch (Throwable e) {
                e.printStackTrace();
                LOG.log(Level.SEVERE, "error in onopen", e);
            }

        }
    }

    private class KeepAliveSender extends Thread {

        private AtomicBoolean stop = new AtomicBoolean(false);

        public void run() {
            while (!stop.get()) {
                try {
                    Thread.sleep(TimeUnit.SECONDS.toMillis(KEEPALIVE_TIMEOUT_SECONDS));

                    LOG.info("Sending keep alive for " + this);
                    CompletableFuture fut = sendKeepAlive();
                    Object get = fut.get(10, TimeUnit.SECONDS);
                    LOG.info("got keepalive for " + get);
                } catch (Throwable e) {
                    LOG.info("FAILED Sending keep alive for " + this);
                    // LOG.log(Level.WARNING, "error in keepalive", e);
                    LOG.info("Closing networkclient " + LegacyNetworkClient.this);
                    LegacyNetworkClient.this.shutdown();
                    LOG.info("Closed networkclient " + LegacyNetworkClient.this);
                }
            }
            LOG.info("No more keepalives for " + this);
        }

        public void shutdownKeepAlive() {
            LOG.info("Requesting to stop keep alive for " + this);
            stop.set(true);
        }
    }

}
