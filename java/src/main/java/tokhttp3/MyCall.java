package tokhttp3;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    
    public MyCall(HttpClient client, Request request) {
        this.httpClient = client;
        this.request = request;
    }

    @Override
    public Response execute() throws IOException {
        try {
            System.err.println("HEADERSSSS: "+request.getHttpRequest().headers().map());
            HttpResponse.BodyHandler responseBodyHandler = BodyHandlers.ofByteArray();
          //  if (request.getHttpRequest().headers().firstValue("Content-Type").isPresent()) {
                responseBodyHandler = BodyHandlers.ofString();
           // }
            System.err.println("SENDREQUEST "+request.getHttpRequest());
            HttpRequest httpRequest = request.getHttpRequest();
            System.err.println("HEADERS = " + httpRequest.headers());
            HttpResponse httpResponse = this.httpClient.send(request.getHttpRequest(), BodyHandlers.ofString());
            System.err.println("RESPONSEHEADERS = " + httpResponse.headers().map());
            Response answer = new Response(httpResponse);
            
            return answer;
        } catch (InterruptedException ex) {
            throw new IOException (ex);
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
