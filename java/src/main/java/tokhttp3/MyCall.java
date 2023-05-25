package tokhttp3;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.signal.libsignal.grpc.GrpcClient;
import org.signal.libsignal.grpc.SignalRpcMessage;
import org.signal.libsignal.grpc.SignalRpcReply;

/**
 *
 * @author johan
 */
public class MyCall implements Call {

    private final HttpClient httpClient;
    private final HttpRequest httpRequest;
    static final ExecutorService executor = Executors.newFixedThreadPool(1); // avoid race issues for now
    private static final Logger LOG = Logger.getLogger(MyCall.class.getName());
    private byte[] rawBody = new byte[0];
    static boolean useGrpc = false;

    static {
        useGrpc = Boolean.getBoolean("wave.grpc");
        LOG.info("do we have grpc? " + System.getProperty("wave.grpc") + ", answer = " + useGrpc);
        if (useGrpc) {
            String target = "https://grpcproxy2.gluonhq.net";
            String sysTarget = System.getProperty("grpc.target");
            if (sysTarget != null) {
                target = sysTarget;
            }
            LOG.info("grpc target for grpcClient = " + target);
            grpcClient = new GrpcClient(target);
        }
    }

    @Deprecated
    public MyCall(HttpClient client, Request request) {
        this.httpClient = client;
        this.httpRequest = request.getHttpRequest();
    }

    public MyCall(HttpClient client, HttpRequest request, byte[] raw) {
        this.httpClient = client;
        this.httpRequest = request;
        this.rawBody = raw;
    }

    @Override
    public Response execute() throws IOException {
        LOG.finest("Executing call " + this);
        LOG.finest("client = "+this.httpClient);
        LOG.info("Execute call to RequestURI = "+httpRequest.uri());
        LOG.finest("RequestMethod = "+httpRequest.method());
        LOG.finest("RequestHeaders = "+httpRequest.headers().map());
        try {
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
            ;

            };
            if (useGrpc) {
                String method = httpRequest.method();
                Map<String, List<String>> realHeaders = httpRequest.headers().map();
                URI uri = httpRequest.uri();
                LOG.info("Make grpc call, URLI = " + uri);
                httpRequest.bodyPublisher().ifPresent(bp -> System.err.println("BP = " + bp));
                Response ranswer = getGrpcConnection(uri, method, rawBody, realHeaders);
                LOG.info("GRPC answer size = " + ranswer.body().bytes().length);
                LOG.finest("GRPC answer = " + Arrays.toString(ranswer.body().bytes()));
                return ranswer;
            }
            LOG.info("MakeLegacy request with headers " + httpRequest.headers().map().keySet());
            HttpResponse httpResponse = this.httpClient.send(this.httpRequest, mbh);

            Response answer = new Response(httpResponse);
            LOG.info("legacy answer size = " + answer.body().bytes().length);

            LOG.info("Done executing call " + this);
            return answer;
        } catch (InterruptedException ex) {
            throw new IOException(ex);
        }
    }

    public void cancel() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    public void enqueue(Callback callback) {
        Runnable r = () -> {
            try {
                Response response = execute();
                callback.onResponse(this, response);
            } catch (IOException ex) {
                Logger.getLogger(MyCall.class.getName()).log(Level.SEVERE, null, ex);
            }
        };
        executor.submit(r);
    }

    private Response getGrpcConnection(URI uri, String method, byte[] body, Map headers) {
        SignalRpcMessage request = new SignalRpcMessage();
        request.setUrlFragment(uri.toString());
        request.setBody(body);
        request.setHeaders(headers);
        request.setMethod(method);
        LOG.info("Getting ready to send DM to proxy");
        SignalRpcReply sReply = getGrpcClient().sendDirectMessage(request);
        LOG.info("Statuscode = " + sReply.getStatusCode());
        LOG.info("Length of answer = " + sReply.getMessage().length);
        return new Response(sReply);
    }
    static GrpcClient grpcClient;

    static synchronized GrpcClient getGrpcClient() {
        if (grpcClient == null) {
            String target = "https://grpcproxy2.gluonhq.net";
            String sysTarget = System.getProperty("grpc.target");
            if (sysTarget != null) {
                target = sysTarget;
            }
            LOG.info("grpc target for grpcClient = " + target);
            grpcClient = new GrpcClient(target);
        }
        return grpcClient;
    }
}
