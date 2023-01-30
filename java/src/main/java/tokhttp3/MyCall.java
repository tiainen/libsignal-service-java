package tokhttp3;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author johan
 */
public class MyCall implements Call {

    private HttpClient httpClient;
    private Request request;

    public MyCall(HttpClient client, Request request) {
        this.httpClient = client;
        this.request = request;
    }

    @Override
    public Response execute() throws IOException {
        try {
            HttpResponse.BodyHandler responseBodyHandler = BodyHandlers.ofString();
            System.err.println("SENDREQUEST "+request.getHttpRequest());
            HttpResponse httpResponse = this.httpClient.send(request.getHttpRequest(), responseBodyHandler);
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
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

}
