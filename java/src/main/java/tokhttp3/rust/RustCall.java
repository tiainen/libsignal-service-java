package tokhttp3.rust;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;
import okio.Buffer;
import tokhttp3.Call;
import tokhttp3.Callback;
import tokhttp3.Request;
import tokhttp3.Response;

public class RustCall implements Call {

    static {
        System.loadLibrary("rust_ech_rustls");
    }

    private static final Logger LOG = Logger.getLogger(RustCall.class.getName());

    private static final ExecutorService executor = Executors.newFixedThreadPool(1); // avoid race issues for now

    private final Request request;
    private Future<?> future;

    public RustCall(Request request) {
        this.request = request;
    }

    @Override
    public Response execute() throws IOException {
        HttpRequest httpRequest = request.getHttpRequest();
        System.out.println("[JSDBG] HTTP Request: Method: " + httpRequest.method());
        System.out.println("[JSDBG] HTTP Request: URI: " + httpRequest.uri());
        System.out.println("[JSDBG] HTTP Request: Headers: " + httpRequest.headers().map());
        RustResponse response = nativeRequest(httpRequest.method(), httpRequest.uri().getHost(),
            httpRequest.uri().getPort(), httpRequest.uri().getPath(), httpRequest.uri().getQuery(),
            httpRequest.headers().map(), request.getBody(), request.getBody() != null ? request.getBody().length : 0);
        return new Response(new RustHttpResponse(httpRequest, response));
    }

    @Override
    public void cancel() {
        if (future == null) {
            future.cancel(true);
        }
    }

    @Override
    public void enqueue(Callback callback) {
        Runnable r = () -> {
            try {
                Response response = execute();
                callback.onResponse(this, response);
            } catch (IOException ex) {
                LOG.log(Level.SEVERE, null, ex);
                callback.onFailure(this, ex);
            }
        };
        future = executor.submit(r);
    }

    public native RustResponse nativeRequest(String method, String host, int port, String path, String query, Map<String, List<String>> headers, byte[] body, int bodyLength);
}
