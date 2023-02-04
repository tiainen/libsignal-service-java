package tokhttp3;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author johan
 */
public class MyCall implements Call {

    private HttpClient httpClient;
    private Request request;
    static final ExecutorService executor = Executors.newFixedThreadPool(1); // avoid race issues for now
    private static final Logger LOG = Logger.getLogger(MyCall.class.getName());

    public MyCall(HttpClient client, Request request) {
        this.httpClient = client;
        this.request = request;
    }

    @Override
    public Response execute() throws IOException {
        try {
            HttpResponse.BodyHandler mbh = new HttpResponse.BodyHandler() {
                @Override
                public HttpResponse.BodySubscriber apply(HttpResponse.ResponseInfo responseInfo) {
                    String ct = responseInfo.headers().firstValue("content-type").orElse("");
                    LOG.info("response content-type = " + ct);
                    if (ct.isBlank()) {
                        return BodySubscribers.discarding();
                    }
                    if (ct.equals("application/json")) {
                        return BodySubscribers.ofString(StandardCharsets.UTF_8);
                    } else {
                        return BodySubscribers.ofByteArray();
                    }
                }
            ;

            };
            HttpRequest httpRequest = request.getHttpRequest();
            HttpResponse httpResponse = this.httpClient.send(request.getHttpRequest(), mbh);
            Response answer = new Response(httpResponse);

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

}
