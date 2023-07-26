package com.gluonhq.snl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.protobuf.ByteString;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscribers;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.whispersystems.signalservice.api.crypto.UnidentifiedAccess;
import org.whispersystems.signalservice.api.messages.SignalServiceEnvelope;
import org.whispersystems.signalservice.api.push.exceptions.AuthorizationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.DeprecatedVersionException;
import org.whispersystems.signalservice.api.push.exceptions.ExpectationFailedException;
import org.whispersystems.signalservice.api.push.exceptions.MalformedResponseException;
import org.whispersystems.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import org.whispersystems.signalservice.api.push.exceptions.NotFoundException;
import org.whispersystems.signalservice.api.push.exceptions.ProofRequiredException;
import org.whispersystems.signalservice.api.push.exceptions.PushNetworkException;
import org.whispersystems.signalservice.api.push.exceptions.RateLimitException;
import org.whispersystems.signalservice.api.push.exceptions.ServerRejectedException;
import org.whispersystems.signalservice.api.push.exceptions.UnregisteredUserException;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
import org.whispersystems.signalservice.internal.ServiceResponse;
import org.whispersystems.signalservice.internal.configuration.SignalUrl;
import org.whispersystems.signalservice.internal.push.AuthCredentials;
import org.whispersystems.signalservice.internal.push.DeviceLimit;
import org.whispersystems.signalservice.internal.push.DeviceLimitExceededException;
import org.whispersystems.signalservice.internal.push.GroupMismatchedDevices;
import org.whispersystems.signalservice.internal.push.LockedException;
import org.whispersystems.signalservice.internal.push.MismatchedDevices;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList;
import org.whispersystems.signalservice.internal.push.ProofRequiredResponse;
import org.whispersystems.signalservice.internal.push.PushServiceSocket;
import org.whispersystems.signalservice.internal.push.SendGroupMessageResponse;
import org.whispersystems.signalservice.internal.push.SendMessageResponse;
import org.whispersystems.signalservice.internal.push.StaleDevices;
import org.whispersystems.signalservice.internal.push.exceptions.GroupMismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.MismatchedDevicesException;
import org.whispersystems.signalservice.internal.push.exceptions.StaleDevicesException;
import org.whispersystems.signalservice.internal.util.JsonUtil;
import org.whispersystems.signalservice.internal.util.Util;
import org.whispersystems.signalservice.internal.util.concurrent.FutureTransformers;
import org.whispersystems.signalservice.internal.util.concurrent.ListenableFuture;
import org.whispersystems.signalservice.internal.util.concurrent.SettableFuture;
import org.whispersystems.signalservice.internal.websocket.DefaultResponseMapper;
import org.whispersystems.signalservice.internal.websocket.ResponseMapper;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketMessage;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;
import org.whispersystems.signalservice.internal.websocket.WebsocketResponse;
import org.whispersystems.util.Base64;

/**
 *
 * @author johan
 */
public abstract class NetworkClient {

    private boolean useQuic;

    final SignalUrl signalUrl;
    final String signalAgent;
    final boolean allowStories;
    final Optional<CredentialsProvider> credentialsProvider;
    final Optional<ConnectivityListener> connectivityListener;
    private static final Logger LOG = Logger.getLogger(NetworkClient.class.getName());

    final BlockingQueue<byte[]> rawByteQueue = new LinkedBlockingQueue<>();
    private final BlockingQueue<WebSocketRequestMessage> wsRequestMessageQueue = new LinkedBlockingQueue<>();

    private final Map<Long, OutgoingRequest> outgoingRequests = new HashMap<>();

    private Thread formatProcessingThread;

    private boolean closed = false;
    private static final String SERVER_DELIVERED_TIMESTAMP_HEADER = "X-Signal-Timestamp";
    private boolean websocketCreated = false;
    
    public static NetworkClient createNetworkClient(SignalUrl url, String agent, boolean allowStories) {
        return createNetworkClient(url, Optional.empty(), agent, Optional.empty(), allowStories);
    }
    public static NetworkClient createNetworkClient(SignalUrl url, Optional<CredentialsProvider> cp, String agent, Optional<ConnectivityListener> cl, boolean allowStories) {
        String property = System.getProperty("wave.quic", "true");
        boolean useQuic = "true".equals(property.toLowerCase());
        LOG.info("Creating networkclient, using quic? "+ useQuic);
        if (useQuic) {
            return new QuicNetworkClient(url, cp, agent, cl, allowStories);
        } else {
            return new LegacyNetworkClient(url, Optional.empty(), agent, Optional.empty(), allowStories);
        }
    }

    public NetworkClient(SignalUrl url, Optional<CredentialsProvider> cp, String signalAgent, Optional<ConnectivityListener> connectivityListener, boolean allowStories) {
        String property = System.getProperty("wave.quic", "false");

        this.useQuic = "true".equals(property.toLowerCase());
        this.signalUrl = url;
        this.signalAgent = signalAgent;
        this.allowStories = allowStories;
        this.credentialsProvider = cp;
        this.connectivityListener = connectivityListener;
        LOG.info("Created NetworkClient with URL " + url + ", cp = " + cp + " and cl = "
                + connectivityListener);
        this.formatProcessingThread = new Thread() {
            @Override
            public void run() {
                processFormatConversion();
            }
        };
        this.formatProcessingThread.start();
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
        implCreateWebSocket(baseUrl);
        websocketCreated = true;
    }

    void implCreateWebSocket(String baseurl) throws IOException {
        throw new UnsupportedOperationException();
    }

    void sendToStream(byte[] payload) throws IOException {
        throw new UnsupportedOperationException();
    }

    /**
     * Close this networkclient and release all resources.
     */
    public void shutdown() {
        this.closed = true;

    }

    void implShutdown() {
    }

    /**
     * Sends a message to a group. This method returns immediately with a Future
     *
     * @param body
     * @param joinedUnidentifiedAccess
     * @param timestamp
     * @param online
     * @return
     * @throws IOException
     */
    public Future<SendGroupMessageResponse> sendToGroup(byte[] body, byte[] joinedUnidentifiedAccess, long timestamp, boolean online) throws IOException {
        if (closed) {
            throw new IOException("Trying to use a closed networkclient " + this);
        }
        List<String> headers = new LinkedList<String>() {
            {
                add("content-type:application/vnd.signal-messenger.mrm");
                add("Unidentified-Access-Key:" + Base64.encodeBytes(joinedUnidentifiedAccess));
            }
        };
        String path = String.format(Locale.US, "/v1/messages/multi_recipient?ts=%s&online=%s", timestamp, online);
        LOG.info("Sending groupmessage to " + path);
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

    // placeholder for using a bidirection stream to send 1-1 messages
    public Future<SendMessageResponse> sendDirectOverStream(OutgoingPushMessageList list,Optional<UnidentifiedAccess> unidentifiedAccess, boolean story) throws IOException {
        List<String> headers = new LinkedList<String>() {
            {
                add("content-type:application/json");
            }
        };
        unidentifiedAccess.ifPresent(ua -> headers.add("Unidentified-Access-Key:" + Base64.encodeBytes(unidentifiedAccess.get().getUnidentifiedAccessKey())));
        WebSocketRequestMessage requestMessage = WebSocketRequestMessage.newBuilder()
                .setId(new SecureRandom().nextLong())
                .setVerb("PUT")
                .setPath(String.format("/v1/messages/%s?story=%s", list.getDestination(), story ? "true" : "false"))
                .addAllHeaders(headers)
                .setBody(ByteString.copyFrom(JsonUtil.toJson(list).getBytes()))
                .build();
        ListenableFuture<WebsocketResponse> response = sendRequest(requestMessage);
        ResponseMapper<SendMessageResponse> responseMapper = DefaultResponseMapper.extend(SendMessageResponse.class)
                .withResponseMapper((status, body, getHeader, unidentified) -> {
                    SendMessageResponse sendMessageResponse = Util.isEmpty(body) ? new SendMessageResponse(false, unidentified)
                            : JsonUtil.fromJsonResponse(body, SendMessageResponse.class);
                    sendMessageResponse.setSentUnidentfied(unidentified);

                    return ServiceResponse.forResult(sendMessageResponse, status, body);
                })
                .withCustomError(404, (status, body, getHeader) -> new UnregisteredUserException(list.getDestination(), new NotFoundException("not found")))
                .build();
        ListenableFuture<SendMessageResponse> answer = FutureTransformers.map(response, value -> {
            int status = value.getStatus();
            LOG.info(signalAgent);
            validateWebsocketResponse(value);
            if (status == 404) {
                throw new UnregisteredUserException(list.getDestination(), new NotFoundException("not found"));     
            }
            String body = value.getBody();
            boolean una = value.isUnidentified();
            LOG.info("Got Value from directSend: " + value+" with status = "+status);
            SendMessageResponse sendMessageResponse = Util.isEmpty(body) ? new SendMessageResponse(false, una)
                    : JsonUtil.fromJsonResponse(body, SendMessageResponse.class);
            sendMessageResponse.setSentUnidentfied(una);
            return sendMessageResponse;
        });
// return FutureTransformers.map(response, responseMapper);
    return answer;
    }

    public boolean isConnected() {
        return true;
    }

    /**
     * Used by the ProvisioningManager which has its own processing. This method
     * should never be mixed with other read methods.
     *
     * @return
     */
    public WebSocketRequestMessage readRequestMessage(long timeout, TimeUnit unit) {
        try {
            if (!this.websocketCreated) {
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
                LOG.info("Need to CreateWebSocket for " + this);
                createWebSocket();
            } catch (IOException ex) {
                Logger.getLogger(NetworkClient.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        try {
            while (true) { // we only return existing envelopes
                LOG.info("Wait for requestMessage...");
                WebSocketRequestMessage request = wsRequestMessageQueue.take();//poll(timeout, unit);
                LOG.info("Got requestMessage, process now " + request.getVerb()+" " + request.getPath());
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
        LOG.info("Handle WS requestMessage ");
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
            LOG.info("[NC] sendResponse to websocket request msg");
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

    CompletableFuture<Response> getKwikResponse(URI uri, String method, byte[] body, Map<String, List<String>> headers) throws IOException {
        throw new UnsupportedOperationException();
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
                LOG.finest("Got raw bytes: " + Arrays.toString(raw));
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
                                        message.getResponse().getHeadersList(), true));
                        // TODO: true means unidentified, this is not always the case!
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

    /**
     * Sends a request and blocks forever until there is a response
     *
     * @param request
     * @param raw
     * @return
     * @throws IOException
     */
    public Response sendRequest(HttpRequest request, byte[] raw) throws IOException {
        try {
            return asyncSendRequest(request, raw).get(); //.get(30, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException ex) {
            LOG.log(Level.SEVERE, null, ex);
            throw new IOException(ex);
        }
    }

    /**
     * Sends a request and immediately return a Future.
     *
     * @param request
     * @param raw
     * @return
     * @throws IOException
     */
    protected CompletableFuture<Response> asyncSendRequest(HttpRequest request, byte[] raw) throws IOException {
        if (closed) {
            throw new IOException("Trying to use a closed networkclient " + this);
        }
        CompletableFuture<Response> response = implAsyncSendRequest(request, raw);
        response.thenApply(res -> validateResponse(res));
        return response;
    }

    protected abstract CompletableFuture<Response> implAsyncSendRequest(HttpRequest request, byte[] raw) throws IOException;

    private Response validateResponse(Response response) {
        try {
            int statusCode = response.getStatusCode();
            switch (statusCode) {
                case 409:
                    LOG.info("Got a 409 exception, throw MMDE");
                    MismatchedDevices mismatchedDevices = readResponseJson(response, MismatchedDevices.class);
                    throw new MismatchedDevicesException(mismatchedDevices);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return response;
    }

    private synchronized ListenableFuture<WebsocketResponse> sendRequest(WebSocketRequestMessage request) throws IOException {
        if (closed) {
            throw new IOException("Trying to use a closed networkclient " + this);
        }
        WebSocketMessage message = WebSocketMessage.newBuilder()
                .setType(WebSocketMessage.Type.REQUEST)
                .setRequest(request).build();
        SettableFuture<WebsocketResponse> future = new SettableFuture<>();
        outgoingRequests.put(request.getId(), new OutgoingRequest(future, System.currentTimeMillis()));
        this.sendToStream(message.toByteArray());
        return future;
    }

    private static <T> T readResponseJson(WebsocketResponse response, Class<T> clazz)
            throws PushNetworkException, MalformedResponseException {
        return readBodyJson(response.getBody(), clazz);
    }

    private static <T> T readResponseJson(Response response, Class<T> clazz)
            throws PushNetworkException, MalformedResponseException {
        return readBodyJson(response.body().string(), clazz);
    }

    private static <T> T readBodyJson(String json, Class<T> clazz) throws PushNetworkException, MalformedResponseException {
      //  String json = body.string();
        try {
            return JsonUtil.fromJson(json, clazz);
        } catch (JsonProcessingException e) {
            LOG.log(Level.SEVERE, "error parsing json response", e);
            throw new MalformedResponseException("Unable to parse entity", e);
        } catch (IOException e) {
            throw new PushNetworkException(e);
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
    
private WebsocketResponse validateWebsocketResponse(WebsocketResponse response)
            throws NonSuccessfulResponseCodeException, PushNetworkException, MalformedResponseException {

        int responseCode = response.getStatus();
        String responseMessage = response.getBody();

        switch (responseCode) {
            case 413:
            case 429: {
                long retryAfterLong = Util.parseLong(response.getHeader("Retry-After"), -1);
                Optional<Long> retryAfter = retryAfterLong != -1 ? Optional.of(TimeUnit.SECONDS.toMillis(retryAfterLong)) : Optional.empty();
                throw new RateLimitException(responseCode, "Rate limit exceeded: " + responseCode, retryAfter);
            }
            case 401:
            case 403:
                throw new AuthorizationFailedException(responseCode, "Authorization failed!");
            case 404:
                throw new NotFoundException("Not found");
            case 409:
                MismatchedDevices mismatchedDevices = readResponseJson(response, MismatchedDevices.class);

                throw new MismatchedDevicesException(mismatchedDevices);
            case 410:
                StaleDevices staleDevices = readResponseJson(response, StaleDevices.class);

                throw new StaleDevicesException(staleDevices);
            case 411:
                DeviceLimit deviceLimit = readResponseJson(response, DeviceLimit.class);

                throw new DeviceLimitExceededException(deviceLimit);
            case 417:
                throw new ExpectationFailedException();
            case 423:
                PushServiceSocket.RegistrationLockFailure accountLockFailure = readResponseJson(response, PushServiceSocket.RegistrationLockFailure.class);
                AuthCredentials credentials = accountLockFailure.backupCredentials;
                String basicStorageCredentials = credentials != null ? credentials.asBasic() : null;

                throw new LockedException(accountLockFailure.length,
                        accountLockFailure.timeRemaining,
                        basicStorageCredentials);
            case 428:
                LOG.info("Whoops, PSS got statuscode 428");
                ProofRequiredResponse proofRequiredResponse = readResponseJson(response, ProofRequiredResponse.class);
                long retryAfter = -1;
                try {
                    String retryAfterRaw = response.getHeader("Retry-After");
                    retryAfter = Util.parseInt(retryAfterRaw, -1);
                    LOG.info("Not good, got a HTTP 428 with content " + response.getBody());
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Logger.getLogger(PushServiceSocket.class.getName()).log(Level.SEVERE, null, ex);
                }
                throw new ProofRequiredException(proofRequiredResponse, retryAfter);

            case 499:
                throw new DeprecatedVersionException();

            case 508:
                throw new ServerRejectedException();
        }

        if (responseCode != 200 && responseCode != 202 && responseCode != 204) {
            throw new NonSuccessfulResponseCodeException(responseCode, "Bad response: " + responseCode + " " + responseMessage);
        }

        return response;
    }
}
