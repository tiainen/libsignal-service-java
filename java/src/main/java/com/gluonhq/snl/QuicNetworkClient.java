package com.gluonhq.snl;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import io.privacyresearch.grpcproxy.SignalRpcMessage;
import io.privacyresearch.grpcproxy.SignalRpcReply;
import io.privacyresearch.grpcproxy.client.QuicClientTransport;
import io.privacyresearch.grpcproxy.client.QuicSignalLayer;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.whispersystems.signalservice.api.util.CredentialsProvider;
import org.whispersystems.signalservice.api.websocket.ConnectivityListener;
import org.whispersystems.signalservice.internal.configuration.SignalUrl;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessage;
import org.whispersystems.signalservice.internal.push.OutgoingPushMessageList;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketMessage;
import org.whispersystems.signalservice.internal.websocket.WebSocketProtos.WebSocketRequestMessage;

/**
 *
 * @author johan
 */
public class QuicNetworkClient extends NetworkClient {

    private static final Logger LOG = Logger.getLogger(QuicNetworkClient.class.getName());
    private final QuicSignalLayer kwikSender;
    final String kwikAddress; // = "swave://localhost:7443";
    // "swave://grpcproxy.gluonhq.net:7443";
    private QuicClientTransport.ControlledQuicStream kwikStream;
    private final ExecutorService directExecutorService = Executors.newFixedThreadPool(1);


    public QuicNetworkClient(SignalUrl url, String agent, boolean allowStories) {
        this(url, Optional.empty(), agent, Optional.empty(), allowStories);
    }

    public QuicNetworkClient(SignalUrl url, Optional<CredentialsProvider> cp, String signalAgent, Optional<ConnectivityListener> connectivityListener, boolean allowStories) {
        super(url, cp, signalAgent, connectivityListener, allowStories);
        URI uri = null;
        this.kwikAddress = System.getProperty("wave.kwikhost", "swave://grpcproxy.gluonhq.net:7443");

        try {
            uri = new URI(kwikAddress);
        } catch (URISyntaxException ex) {
            LOG.log(Level.SEVERE, "wrong format for quic address", ex);
            LOG.warning("Fallback to non-quic transport");
        }
        this.kwikSender = (uri == null ? null : new QuicSignalLayer(uri));
    }

    @Override
    protected CompletableFuture<Response> asyncSendRequest(HttpRequest request, byte[] raw) throws IOException {

        CompletableFuture<Response> response;
        LOG.info("Send request, using kwik");
        URI uri = request.uri();
        String method = request.method();
        Map headers = request.headers().map();
        CompletableFuture<Response> answer = getKwikResponse(uri, method, raw, headers);
        LOG.info("Got request, using kwik");
        response = answer;
        return response;
    }

    @Override
    void implCreateWebSocket(String baseUrl)  throws IOException {
        Map<String, String> headerMap = new HashMap<>();
        headerMap.put("X-Signal-Agent", signalAgent);
        headerMap.put("X-Signal-Receive-Stories", allowStories ? "true" : "false");
        Consumer<byte[]> gotData = reply -> {
            try {
                LOG.info("WS Got reply");
                SignalRpcReply signalReply = SignalRpcReply.parseFrom(reply);
                LOG.info("Reply has statuscode "+signalReply.getStatuscode());
                rawByteQueue.put(signalReply.getMessage().toByteArray());
            } catch (InterruptedException ex) {
                Logger.getLogger(NetworkClient.class.getName()).log(Level.SEVERE, null, ex);
            } catch (InvalidProtocolBufferException ex) {
                Logger.getLogger(NetworkClient.class.getName()).log(Level.SEVERE, null, ex);
            } catch (Throwable t) {
                t.printStackTrace();
            }
        };
        this.kwikStream = kwikSender.openControlledStream(baseUrl, headerMap, gotData);
    }

    @Override
    CompletableFuture<Response> getKwikResponse(URI uri, String method, byte[] body, Map<String, List<String>> headers) throws IOException {
        SignalRpcMessage.Builder requestBuilder = SignalRpcMessage.newBuilder();
        requestBuilder.setUrlfragment(uri.toString());
        requestBuilder.setBody(ByteString.copyFrom(body));
        headers.entrySet().forEach(header -> {
            header.getValue().forEach(hdr -> {
                requestBuilder.addHeader(header.getKey() + "=" + hdr);
            });
        });
        requestBuilder.setMethod(method);
        LOG.info("Getting ready to send DM to kwikproxy with method = "+method+", body length = "+body.length+" and hl = "+headers.size());
        CompletableFuture<SignalRpcReply> sReplyFuture = sendSignalMessage(requestBuilder.build());
        CompletableFuture<Response> answer = sReplyFuture.thenApply(reply -> new Response<byte[]>(reply.getMessage().toByteArray(), reply.getStatuscode()));
        return answer;
    }
    
    private CompletableFuture<SignalRpcReply> sendSignalMessage(SignalRpcMessage msg) {
        return kwikSender.sendSignalMessage(msg);
    }

    @Override
    protected CompletableFuture<Response> implAsyncSendRequest(HttpRequest request, byte[] raw) throws IOException {
        LOG.info("Send request, using kwik");
        URI uri = request.uri();
        String method = request.method();
        Map headers = request.headers().map();
        CompletableFuture<Response> response = getKwikResponse(uri, method, raw, headers);
        LOG.info("Got request, using kwik");
        return response;
    }

    @Override
    void sendToStream(WebSocketMessage msg, OutgoingPushMessageList list) throws IOException {
        msg = convertMessage(msg, list);
        SignalRpcMessage.Builder builder = SignalRpcMessage.newBuilder();
        builder.setBody(ByteString.copyFrom(msg.toByteArray()));
        SignalRpcMessage signalMessage = builder.build();
        this.kwikSender.writeMessageToStream(kwikStream, signalMessage);
    }

    /**
     * Remove json messages from the WebSocketMessage, and replace with serialized
     * OutgoingPushMessageList
     * @param msg
     * @param list
     * @return 
     */
    WebSocketMessage convertMessage(WebSocketMessage msg, OutgoingPushMessageList list) {
        if ((list == null) || (msg.getRequest() == null)) return msg;
        WebSocketRequestMessage wrm = msg.getRequest();
        WebSocketProtos.WebSocketRequestMessage.Builder newBuilder = wrm.toBuilder();
        newBuilder.setBody(ByteString.EMPTY);
        WebSocketProtos.PushMessageList.Builder pmlBuilder = WebSocketProtos.PushMessageList.newBuilder();
        pmlBuilder.setDestination(list.getDestination())
                .setTimestamp(list.getTimestamp())
                .setUrgent(list.isUrgent())
                .setOnline(list.isOnline());
        for (OutgoingPushMessage opm : list.getMessages()) {
            WebSocketProtos.PushMessage pm = WebSocketProtos.PushMessage.newBuilder()
                    .setContent(opm.getContent())
                    .setDestinationDeviceId(opm.getDestinationDeviceId())
                    .setDestinationRegistrationId(opm.getDestinationRegistrationId())
                    .setType(opm.getType()).build();
            pmlBuilder.addPushMessage(pm);
        }
        newBuilder.setPushMessageList(pmlBuilder.build());
        WebSocketProtos.WebSocketRequestMessage newRequestMessage = newBuilder.build();
        int newSize = newRequestMessage.toByteArray().length;
        int oldSize = wrm.toByteArray().length;
        LOG.info("converted websocketmessage from OLD SIZE = "+oldSize+" to newSize = "+newSize);
        return msg.toBuilder().setRequest(newRequestMessage).build();
    }
}
