package tokhttp3;

import io.grpc.stub.StreamObserver;
import io.privacyresearch.grpcproxy.SignalRpcMessage;
import io.privacyresearch.grpcproxy.SignalRpcReply;
import java.util.logging.Logger;
import okio.ByteString;
import io.privacyresearch.grpcproxy.client.GrpcConfig;
import io.privacyresearch.grpcproxy.client.StreamListener;
import io.privacyresearch.grpcproxy.client.TunnelClient;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.signal.libsignal.grpc.GrpcClient;
import org.signal.libsignal.grpc.GrpcReplyListener;

/**
 *
 * @author johan
 */
public class GrpcWebSocket implements WebSocket {

    private static final Logger LOG = Logger.getLogger(GrpcWebSocket.class.getName());
    private TunnelClient tunnelClient;
    StreamObserver<SignalRpcMessage> rpcStream;
    private GrpcClient grpcClient;
    boolean useLibsignal = true;

    public GrpcWebSocket(WebSocketListener listener) throws IOException {
        GrpcConfig config = new GrpcConfig();
        config.useTLS(false);
        String target = "https://grpcproxy2.gluonhq.net";
        String sysTarget = System.getProperty("grpc.target");
        if (sysTarget != null) {
            target = sysTarget;
        }
        LOG.info("Create grpcWebsocket to tunnel " + target);
        config.target(target);
        if (useLibsignal) {
            this.grpcClient = new GrpcClient(target);
        } else {
            tunnelClient = new TunnelClient(config);
        }
    }

    public void open(String destinationUri, Map<String, String> headers, WebSocketListener listener) {
        if (!useLibsignal) {
            StreamListener<SignalRpcReply> streamListener = new StreamListener<>() {
                @Override
                public void onNext(SignalRpcReply v) {
                    LOG.info("got message with statuscode  " + v.getStatuscode() + " and length = " + v.getMessage().size());
                    if (v.getStatuscode() == -100) {
                        LOG.info("that's our onopen msg");
                        listener.onOpen(GrpcWebSocket.this, null);
                        return;
                    }
                    ByteBuffer bbuff = v.getMessage().asReadOnlyByteBuffer();
                    ByteString bb = ByteString.of(bbuff);
                    listener.onMessage(GrpcWebSocket.this, bb);
                }
            };
            rpcStream = tunnelClient.openStream(destinationUri, headers, streamListener);
        } else {

            Map<String, List<String>> gHeaders = new HashMap<>();
            headers.forEach((k, v) -> {
                gHeaders.put(k, List.of(v));
            });
            GrpcReplyListener replyListener = new GrpcReplyListener() {
                @Override
                public void onReply(org.signal.libsignal.grpc.SignalRpcReply reply) {
                    LOG.info("got message with statuscode  " + reply.getStatusCode() + " and length = " + reply.getMessage().length);
                    if (reply.getStatusCode() == -100) {
                        LOG.info("that's our onopen msg");
                        listener.onOpen(GrpcWebSocket.this, null);
                        return;
                    }
                    byte[] bbuff = reply.getMessage();
                    ByteString bb = ByteString.of(bbuff);
                    listener.onMessage(GrpcWebSocket.this, bb);
                }

                @Override
                public void onError(String err) {
                    LOG.info("Got message from Grpc server with error! " + err);
                    LOG.severe("Connection closed, need to think about what to do.");
                }
            };
            Thread t = new Thread() {
                @Override public void run() {
                    LOG.info("This thread will open stream on grpcClient");
                    grpcClient.openStream(destinationUri, gHeaders, replyListener);
                }
            };
            LOG.info("Ready to open stream on grpcClient in another Thread");
            t.start();
            LOG.info("Started other thread to open stream on grpcClient");
        }
    }

    @Override
    public boolean close(int code, String reason) {
        LOG.severe("We are asked to close this websocket, reason = "+reason+" and code = "+code);
        return true;
    }

    @Override
    public boolean send(ByteString obs) {
        if (useLibsignal) {
            byte[] b = obs.toByteArray();
            org.signal.libsignal.grpc.SignalRpcMessage srm =
                    new org.signal.libsignal.grpc.SignalRpcMessage();
            LOG.info("Sending "+b.length+" bytes over grpc stream from signal to proxy");
            srm.setBody(b);
            srm.setMethod("");
            srm.setUrlFragment("");
            grpcClient.sendMessageOnStream(srm);
            return true;
        }
        ByteBuffer bb = obs.asByteBuffer();
        SignalRpcMessage srm = SignalRpcMessage.newBuilder()
                .setBody(com.google.protobuf.ByteString.copyFrom(bb)).build();
        rpcStream.onNext(srm);
        return true;
    }

    @Override
    public boolean send(String text) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public long queueSize() {
        return 1;
    }

}
