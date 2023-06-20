package com.gluonhq.snl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
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
import java.util.Arrays;
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
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.signal.libsignal.protocol.logging.Log;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.exceptions.MalformedResponseException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
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

    final boolean useQuic;
    final String kwikAddress; // = "swave://localhost:7443";
            // "swave://grpcproxy.gluonhq.net:7443";
    final HttpClient httpClient;
    final SignalUrl signalUrl;
    final String signalAgent;
    final boolean allowStories;
    final Optional<CredentialsProvider> credentialsProvider;
    final Optional<ConnectivityListener> connectivityListener;
    private static final Logger LOG = Logger.getLogger(NetworkClient.class.getName());

    // only one of those will be used, depending if we use quic or not.
    private WebSocket webSocket;
    private KwikSender.KwikStream kwikStream;

    private KwikSender kwikSender;

    private final BlockingQueue<byte[]> rawByteQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<WebSocketRequestMessage> wsRequestMessageQueue = new LinkedBlockingQueue<>();

    private final Map<Long, OutgoingRequest> outgoingRequests = new HashMap<>();

    private Thread formatProcessingThread;
    KeepAliveSender keepAliveSender;

    private boolean closed = false;
    private static final String SERVER_DELIVERED_TIMESTAMP_HEADER = "X-Signal-Timestamp";
    private static final int KEEPALIVE_TIMEOUT_SECONDS = 55;
    private boolean websocketCreated = false;

    public NetworkClient(SignalUrl url, String agent, boolean allowStories) {
        this(url, Optional.empty(), agent, Optional.empty(), allowStories);
    }

    public NetworkClient(SignalUrl url, Optional<CredentialsProvider> cp, String signalAgent, Optional<ConnectivityListener> connectivityListener, boolean allowStories) {
        String property = System.getProperty("wave.quic", "false");
        System.err.println("NCPROP = "+property);
        this.useQuic =  "true".equals(property.toLowerCase());
        this.kwikAddress = System.getProperty("wave.kwikhost", "swave://grpcproxy.gluonhq.net:7443");
        this.signalUrl = url;
        this.signalAgent = signalAgent;
        this.allowStories = allowStories;
        this.httpClient = buildClient();
        this.credentialsProvider = cp;
        this.connectivityListener = connectivityListener;
        LOG.info("Created NetworkClient with url "+url+", cp = "+cp+" and cl = "
                +connectivityListener+" and httpClient = "+httpClient);
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

    private void createWebSocket() throws IOException {
        LOG.info("Creating websocket");
        String baseUrl = signalUrl.getUrl().replace("https://", "wss://")
                .replace("http://", "ws://");//
        if (!baseUrl.endsWith("provisioning/")) {
            baseUrl = baseUrl + "/v1/websocket/";
        }
        if (this.credentialsProvider.isPresent()) {
            CredentialsProvider cp = this.credentialsProvider.get();
            String identifier = cp.getAci() != null ? cp.getDeviceUuid() : cp.getE164();
            baseUrl = baseUrl + "?login=" + identifier + "&password=" + cp.getPassword();
        }
        if (useQuic) {
            createKwikWebSocket(baseUrl);
        } else {
            createLegacyWebSocket(baseUrl);
        }
        websocketCreated = true;
    }

    private void createKwikWebSocket(String baseUrl)  throws IOException {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("X-Signal-Agent", signalAgent);
        headerMap.put("X-Signal-Receive-Stories", allowStories ? "true" : "false");
        this.kwikSender = new KwikSender(kwikAddress);
        Consumer<byte[]> gotData = reply -> {
            try {
                LOG.info("WS Got reply");
                SignalRpcReply signalReply = SignalRpcReply.parseFrom(reply);
                LOG.info("Reply = "+signalReply);
                rawByteQueue.put(signalReply.getMessage().toByteArray());
            } catch (InterruptedException ex) {
                Logger.getLogger(NetworkClient.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvalidProtocolBufferException ex) {
                Logger.getLogger(NetworkClient.class.getName()).log(Level.SEVERE, null, ex);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        };
        this.kwikStream = kwikSender.openWebSocket(baseUrl, headerMap, gotData);
    }

    private void createLegacyWebSocket(String baseUrl)  throws IOException {
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

    private synchronized CompletableFuture sendKeepAlive() throws IOException {
        byte[] message = WebSocketMessage.newBuilder()
                .setType(WebSocketMessage.Type.REQUEST)
                .setRequest(WebSocketRequestMessage.newBuilder()
                        .setId(System.currentTimeMillis())
                        .setPath("/v1/keepalive")
                        .setVerb("GET")
                        .build()).build()
                .toByteArray();
        System.err.println("KEEPALIVE: "+Arrays.toString(message));
        CompletableFuture fut = CompletableFuture.runAsync(() -> {
            try {
                sendToStream(message);
            } catch (Exception ex) {
                Logger.getLogger(NetworkClient.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        return fut;
    }

    public void shutdown() {
        this.closed = true;
        if (this.keepAliveSender != null) {
            this.keepAliveSender.shutdownKeepAlive();
        }
        if (this.webSocket != null) {
            this.webSocket.abort();
        }
    }

    public Future<SendGroupMessageResponse> sendToGroup(byte[] body, byte[] joinedUnidentifiedAccess, long timestamp, boolean online) throws IOException {
        if (closed) throw new IOException ("Trying to use a closed networkclient "+this);
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

    /**
     * Used by the ProvisioningManager which has its own processing.
     * This method should never be mixed with other read methods.
     * @return
     */
    public WebSocketRequestMessage readRequestMessage(long timeout, TimeUnit unit) {
        try {
            if (this.webSocket == null) {
                createWebSocket();
            }
            WebSocketRequestMessage request = wsRequestMessageQueue.take();//poll(timeout, unit);
            return request;
        } catch (InterruptedException | IOException ex) {
            Logger.getLogger(NetworkClient.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public SignalServiceEnvelope read(long timeout, TimeUnit unit) {
        if (!this.websocketCreated) { // TODO: create WS before starting to read
            try {
                LOG.info("Need to CreateWebSocket for "+this);
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
                sendToStream(msg.toByteArray());
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
        KwikSender kwikSender = new KwikSender(kwikAddress);
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
                LOG.finest("Got raw bytes: "+Arrays.toString(raw));
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
        if (closed) throw new IOException ("Trying to use a closed networkclient "+this);
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
            LOG.info("Got response, not using kwik");
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
            LOG.info("Invoke send on httpClient "+this.httpClient);
            httpResponse = this.httpClient.send(request, createBodyHandler());
            LOG.info("Did invoke send on httpClient");
        } catch (InterruptedException ex) {
            LOG.log(Level.SEVERE, "Error sending using httpClient "+this.httpClient, ex);
            throw new IOException(ex);
        }
        return new Response(httpResponse);
    }

    private void sendToStream(byte[] payload) throws IOException {
        if (useQuic) {
            this.kwikSender.writeMessageToStream(kwikStream, payload);
        } else {
            this.webSocket.sendBinary(ByteBuffer.wrap(payload), true);
        }
    }
    public synchronized ListenableFuture<WebsocketResponse> sendRequest(WebSocketRequestMessage request) throws IOException {
        if (closed) throw new IOException ("Trying to use a closed networkclient "+this);
        WebSocketMessage message = WebSocketMessage.newBuilder()
                .setType(WebSocketMessage.Type.REQUEST)
                .setRequest(request).build();
        SettableFuture<WebsocketResponse> future = new SettableFuture<>();
        outgoingRequests.put(request.getId(), new OutgoingRequest(future, System.currentTimeMillis()));
        this.sendToStream(message.toByteArray());
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
            LOG.log(Level.WARNING, "ERROR IN WEBSOCKET, do we have connectivityListener? "+connectivityListener+", err = "+error);
            connectivityListener.ifPresent(cl -> cl.onError());
        //    reCreateWebSocket();
        //    error.printStackTrace();
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            LOG.info("Websocket closed with statusCode "+statusCode+" and reason "+reason+". Do we have a cl? "+connectivityListener);
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
                NetworkClient.this.keepAliveSender = new KeepAliveSender();
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
                    LOG.info("Closing networkclient "+NetworkClient.this);
                    NetworkClient.this.shutdown();
                    LOG.info("Closed networkclient "+NetworkClient.this);
                }
            }
            LOG.info("No more keepalives for " + this);
        }

        public void shutdownKeepAlive() {
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
