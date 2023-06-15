package com.gluonhq.snl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.ByteString;
import io.privacyresearch.grpcproxy.SignalRpcMessage;
import io.privacyresearch.grpcproxy.SignalRpcReply;
import io.privacyresearch.grpcproxy.client.KwikSender;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscribers;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.signal.libsignal.protocol.logging.Log;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.exceptions.MalformedResponseException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.internal.configuration.SignalUrl;
import org.whispersystems.signalservice.internal.push.GroupMismatchedDevices;
import org.whispersystems.signalservice.internal.push.MismatchedDevices;
import org.whispersystems.signalservice.internal.push.SendGroupMessageResponse;
import org.whispersystems.signalservice.internal.push.exceptions.GroupMismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.internal.util.concurrent.FutureTransformers;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;
import org.whispersystems.signalservice.internal.util.concurrent.SettableFuture;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketMessage;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse;
import org.whispersystems.util.Base64;

/**
 *
 * @author johan
 */
public class NetworkClient {

    boolean useQuic = false;
    final HttpClient httpClient;
    final SignalUrl signalUrl;
    final String signalAgent;
    final boolean allowStories;
    final Optional<CredentialsProvider> credentialsProvider;
    private static final Logger LOG = Logger.getLogger(NetworkClient.class.getName());
    private WebSocket webSocket;
    private BlockingQueue<byte[]> rawByteQueue = new LinkedBlockingQueue<>();
    private BlockingQueue<WebSocketRequestMessage> wsRequestMessageQueue = new LinkedBlockingQueue<>();
    private BlockingQueue<SignalServiceEnvelope> envelopeQueue = new LinkedBlockingQueue<>();

    private final Map<Long, OutgoingRequest> outgoingRequests = new HashMap<>();

    private Thread formatProcessingThread;
    KeepAliveSender keepAliveSender;

    private boolean closed = false;
    private static final String SERVER_DELIVERED_TIMESTAMP_HEADER = "X-Signal-Timestamp";
    private static final int KEEPALIVE_TIMEOUT_SECONDS = 55;

    public NetworkClient(SignalUrl url, String agent, boolean allowStories) {
        this(url, Optional.empty(), agent, allowStories);
    }

    public NetworkClient(SignalUrl url, Optional<CredentialsProvider> cp, String signalAgent, boolean allowStories) {
        this.signalUrl = url;
        this.signalAgent = signalAgent;
        this.allowStories = allowStories;
        this.httpClient = buildClient();
        this.credentialsProvider = cp;
        this.formatProcessingThread = new Thread() {
            @Override
            public void run() {
                processFormatConversion();
            }
        };
        this.formatProcessingThread.start();
    }

    private HttpClient buildClient() {
        HttpClient.Builder clientBuilder = HttpClient.newBuilder();
        HttpClient answer = clientBuilder.build();
        return answer;
    }

    long lastRestart = 0l;

    private void reCreateWebSocket() {
        Thread t = new Thread() {
            @Override
            public void run() {
                if ((System.currentTimeMillis() - lastRestart) < 10000) {
                    LOG.info("Restart requested, but previous request has been less than 10s. Ignore.");
                } else {
                    lastRestart = System.currentTimeMillis();
                    try {
                        createWebSocket();
                    } catch (IOException ex) {
                        Logger.getLogger(NetworkClient.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }
            }
        };
        t.start();
    }

    private void createWebSocket() throws IOException {
        WebSocket.Builder wsBuilder = this.httpClient.newWebSocketBuilder();
        wsBuilder.header("X-Signal-Agent", signalAgent);
        wsBuilder.header("X-Signal-Receive-Stories", allowStories ? "true" : "false");
        String baseUrl = signalUrl.getUrl().replace("https://", "wss://")
                .replace("http://", "ws://") + "/v1/websocket/";
        if (this.credentialsProvider.isPresent()) {
            CredentialsProvider cp = this.credentialsProvider.get();
            String identifier = cp.getAci() != null ? cp.getDeviceUuid() : cp.getE164();
            baseUrl = baseUrl + "?login=" + identifier + "&password=" + cp.getPassword();
        }
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

    private synchronized void sendKeepAlive() throws IOException {
        byte[] message = WebSocketMessage.newBuilder()
                .setType(WebSocketMessage.Type.REQUEST)
                .setRequest(WebSocketRequestMessage.newBuilder()
                        .setId(System.currentTimeMillis())
                        .setPath("/v1/keepalive")
                        .setVerb("GET")
                        .build()).build()
                .toByteArray();
        this.webSocket.sendBinary(ByteBuffer.wrap(message), true);
    }

    public void shutdown() {
        this.closed = true;
        if (this.webSocket != null) {
            this.webSocket.abort();
        }
    }

    public Future<SendGroupMessageResponse> sendToGroup(byte[] body, byte[] joinedUnidentifiedAccess, long timestamp, boolean online) {
        List<String> headers = new LinkedList<String>() {
            {
                add("content-type:application/vnd.signal-messenger.mrm");
                add("Unidentified-Access-Key:" + Base64.encodeBytes(joinedUnidentifiedAccess));
            }
        };
        String path = String.format(Locale.US, "/v1/messages/multi_recipient?ts=%s&online=%s", timestamp, online);
        LOG.info("Sending groupmessage to "+path);
        WebSocketRequestMessage requestMessage = WebSocketRequestMessage.newBuilder()
                .setId(new SecureRandom().nextLong())
                .setVerb("PUT")
                .setPath(path)
                .addAllHeaders(headers)
                .setBody(ByteString.copyFrom(body))
                .build();

        ListenableFuture<WebsocketResponse> response = sendRequest(requestMessage);

        ListenableFuture<SendGroupMessageResponse> answer = FutureTransformers.map(response, value -> {
            if (value.getStatus() == 404) {
                System.err.println("ERROR: sendGroup -> 404");
                Thread.dumpStack();
                throw new IOException();
            } else if (value.getStatus() == 409) {
                GroupMismatchedDevices[] mismatchedDevices = JsonUtil.fromJsonResponse(value.getBody(), GroupMismatchedDevices[].class);
                throw new GroupMismatchedDevicesException(mismatchedDevices);
//        throw new UnregisteredUserException(list.getDestination(), new NotFoundException("not found"));
            } else if (value.getStatus() == 508) {
                throw new ServerRejectedException();
            } else if (value.getStatus() < 200 || value.getStatus() >= 300) {
                System.err.println("will throw IOexception, response = " + value.getBody());
                throw new IOException("Non-successful response: " + value.getStatus());
            }

            if (Util.isEmpty(value.getBody())) {
                return new SendGroupMessageResponse();
            } else {
                return JsonUtil.fromJson(value.getBody(), SendGroupMessageResponse.class);
            }
        });
        return answer;
    }

    public boolean isConnected() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public SignalServiceEnvelope read(long timeout, TimeUnit unit) {
        if (this.webSocket == null) { // TODO: create WS before starting to read
            try {
                createWebSocket();
            } catch (IOException ex) {
                Logger.getLogger(NetworkClient.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        try {
            while (true) { // we only return existing envelopes
                LOG.info("Wait for requestMessage...");
                WebSocketRequestMessage request = wsRequestMessageQueue.take();//poll(timeout, unit);
                LOG.info("Got requestMessage, process now " + request);
                Optional<SignalServiceEnvelope> sse = handleWebSocketRequestMessage(request);
                if (sse.isPresent()) {
                    return sse.get();
                }
            }
        } catch (Exception ex) {
            Logger.getLogger(NetworkClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    Optional<SignalServiceEnvelope> handleWebSocketRequestMessage(WebSocketRequestMessage request) throws IOException {
        WebSocketProtos.WebSocketResponseMessage response = createWebSocketResponse(request);

        try {
            if (isSignalServiceEnvelope(request)) {
                Optional<String> timestampHeader = findHeader(request, SERVER_DELIVERED_TIMESTAMP_HEADER);
                long timestamp = 0;

                if (timestampHeader.isPresent()) {
                    try {
                        timestamp = Long.parseLong(timestampHeader.get());
                    } catch (NumberFormatException e) {
                        LOG.warning("Failed to parse " + SERVER_DELIVERED_TIMESTAMP_HEADER);
                    }
                }

                SignalServiceEnvelope envelope = new SignalServiceEnvelope(request.getBody().toByteArray(), timestamp);
                LOG.finer("Request " + Objects.hashCode(request) + " has envelope " + Objects.hashCode(envelope));
                return Optional.of(envelope);
            } else if (isSocketEmptyRequest(request)) {
                return Optional.empty();
            }
        } finally {
            LOG.finer("[SSMP] readOrEmpty SHOULD send response");
            try {
                WebSocketMessage msg = WebSocketMessage.newBuilder()
                        .setType(WebSocketMessage.Type.RESPONSE)
                        .setResponse(response)
                        .build();
                msg.toByteArray();
                webSocket.sendBinary(ByteBuffer.wrap(msg.toByteArray()), true);
            } catch (Exception ioe) {
                LOG.log(Level.SEVERE, "IO exception in sending response", ioe);
            }
            LOG.fine("[SSMP] readOrEmpty did send response");
        }
        return Optional.empty();
    }

    private boolean isSignalServiceEnvelope(WebSocketRequestMessage message) {
        return "PUT".equals(message.getVerb()) && "/api/v1/message".equals(message.getPath());
    }

    private boolean isSocketEmptyRequest(WebSocketRequestMessage message) {
        return "PUT".equals(message.getVerb()) && "/api/v1/queue/empty".equals(message.getPath());
    }

    private WebSocketProtos.WebSocketResponseMessage createWebSocketResponse(WebSocketRequestMessage request) {
        if (isSignalServiceEnvelope(request)) {
            return WebSocketProtos.WebSocketResponseMessage.newBuilder()
                    .setId(request.getId())
                    .setStatus(200)
                    .setMessage("OK")
                    .build();
        } else {
            return WebSocketProtos.WebSocketResponseMessage.newBuilder()
                    .setId(request.getId())
                    .setStatus(400)
                    .setMessage("Unknown")
                    .build();
        }
    }

    private Response getKwikResponse(URI uri, String method, byte[] body, Map<String, List<String>> headers) throws IOException {
        SignalRpcMessage.Builder requestBuilder = SignalRpcMessage.newBuilder();
        requestBuilder.setUrlfragment(uri.toString());
        requestBuilder.setBody(ByteString.copyFrom(body));
        headers.entrySet().forEach(header -> {
            header.getValue().forEach(hdr -> {
                requestBuilder.addHeader(header.getKey() + "=" + hdr);
            });
        });
        requestBuilder.setMethod(method);
        LOG.info("Getting ready to send DM to kwikproxy");
        KwikSender kwikSender = new KwikSender("swave://grpcproxy.gluonhq.net:7443");
        SignalRpcReply sReply = kwikSender.sendSignalMessage(requestBuilder.build());
        LOG.info("Statuscode = " + sReply.getStatuscode());
        ByteString message = sReply.getMessage();
        LOG.info("Got message, "+message);
        LOG.info("Size = "+message.size());
        
        byte[] raw = sReply.getMessage().toByteArray();
        LOG.info("RawSize = "+raw.length);
        Response<byte[]> answer = new Response<>(raw, sReply.getStatuscode());
//                LOG.info("Length of answer = " + sReply.getMessage().length);
        return answer;
    }

    private static Optional<String> findHeader(WebSocketRequestMessage message, String targetHeader) {
        if (message.getHeadersCount() == 0) {
            return Optional.empty();
        }

        for (String header : message.getHeadersList()) {
            if (header.startsWith(targetHeader)) {
                String[] split = header.split(":");
                if (split.length == 2 && split[0].trim().toLowerCase().equals(targetHeader.toLowerCase())) {
                    return Optional.of(split[1].trim());
                }
            }
        }

        return Optional.empty();
    }

    private void processFormatConversion() {
        LOG.info("start processformatthread");
        while (!closed) {
            try {
                LOG.info("Wait for raw bytes");
                byte[] raw = rawByteQueue.take();
                LOG.info("Got raw bytes");
                WebSocketMessage message = WebSocketMessage.parseFrom(raw);
                LOG.info("Got message, type = " + message.getType());
                if (message.getType() == WebSocketMessage.Type.REQUEST) {
                    LOG.info("Add request message to queue");
                    wsRequestMessageQueue.put(message.getRequest());
                } else if (message.getType() == WebSocketMessage.Type.RESPONSE) {
                    OutgoingRequest listener = outgoingRequests.get(message.getResponse().getId());
                    LOG.info("incoming message is response for request with id " + message.getResponse().getId() + " and listener = " + listener);
                    if (listener != null) {
                        listener.getResponseFuture().set(
                                new WebsocketResponse(message.getResponse().getStatus(),
                                        new String(message.getResponse().getBody().toByteArray()),
                                        message.getResponse().getHeadersList()));
                    }
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }

        }
    }

    HttpResponse.BodyHandler createBodyHandler() {
        HttpResponse.BodyHandler mbh = new HttpResponse.BodyHandler() {
            @Override
            public HttpResponse.BodySubscriber apply(HttpResponse.ResponseInfo responseInfo) {
                String ct = responseInfo.headers().firstValue("content-type").orElse("");
                LOG.info("response statuscode = " + responseInfo.statusCode() + ", content-type = " + ct);
                if (responseInfo.statusCode() == 428) {
                    LOG.info("Got 428 response! all headers = " + responseInfo.headers().map());
                }
                if (ct.isBlank()) {
                    return BodySubscribers.discarding();
                }
                if ((ct.equals("application/json") || (ct.equals("application/xml")))) {
                    return BodySubscribers.ofString(StandardCharsets.UTF_8);
                } else {
                    return BodySubscribers.ofByteArray();
                }
            }
        };
        return mbh;
    }

    public Response sendRequest(HttpRequest request, byte[] raw) throws IOException {
        Response response;
        if (useQuic) {
            LOG.info("Send request, using kwik");
            URI uri = request.uri();
            String method = request.method();
            Map headers = request.headers().map();
            Response answer = getKwikResponse(uri, method, raw, headers);
            LOG.info("Got request, using kwik");
            response = answer;
        } else {
            LOG.info("Send request, not using kwik");
            response = getDirectResponse(request);
        }
        validateResponse(response);
        return response;
    }

    private void validateResponse(Response response) throws MismatchedDevicesException, PushNetworkException, MalformedResponseException {
        int statusCode = response.getStatusCode();
        switch (statusCode) {
            case 409:
                LOG.info("Got a 409 exception, throw MMDE");
                MismatchedDevices mismatchedDevices = readResponseJson(response, MismatchedDevices.class);
                throw new MismatchedDevicesException(mismatchedDevices);
        }
    }
    private Response getDirectResponse(HttpRequest request) throws IOException {
        HttpResponse httpResponse;
        try {
            httpResponse = this.httpClient.send(request, createBodyHandler());
        } catch (InterruptedException ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new IOException(ex);
        }
        return new Response(httpResponse);
    }

    public synchronized ListenableFuture<WebsocketResponse> sendRequest(WebSocketRequestMessage request) {
        WebSocketMessage message = WebSocketMessage.newBuilder()
                .setType(WebSocketMessage.Type.REQUEST)
                .setRequest(request).build();
        SettableFuture<WebsocketResponse> future = new SettableFuture<>();
        outgoingRequests.put(request.getId(), new OutgoingRequest(future, System.currentTimeMillis()));
        webSocket.sendBinary(ByteBuffer.wrap(message.toByteArray()), true);
        return future;
    }

    private static <T> T readResponseJson(Response response, Class<T> clazz)
            throws PushNetworkException, MalformedResponseException {
        return readBodyJson(response.body(), clazz);
    }

    private static <T> T readBodyJson(ResponseBody body, Class<T> clazz) throws PushNetworkException, MalformedResponseException {
        String json = body.string();
        try {
            return JsonUtil.fromJson(json, clazz);
        } catch (JsonProcessingException e) {
            LOG.log(Level.SEVERE, "error parsing json response", e);
            throw new MalformedResponseException("Unable to parse entity", e);
        } catch (IOException e) {
            throw new PushNetworkException(e);
        }
    }

    class MyWebsocketListener implements WebSocket.Listener {

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            LOG.log(Level.SEVERE, "ERROR IN WEBSOCKET!", error);
            reCreateWebSocket();
        //    error.printStackTrace();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            LOG.info("Websocket opened");
            Thread.dumpStack();
            throw new UnsupportedOperationException("Not supported yet.");
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
                KeepAliveSender keepAliveSender = new KeepAliveSender();
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

                    LOG.finest("Sending keep alive for " + this);
                    sendKeepAlive();
                } catch (Throwable e) {
                    LOG.info("FAILED Sending keep alive for " + this);
                    LOG.log(Level.SEVERE, "error in keepalive", e);
                }
            }
            LOG.info("No more keepalives for " + this);
        }

        public void shutdown() {
            LOG.info("Requesting to stop keep alive for " + this);
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
