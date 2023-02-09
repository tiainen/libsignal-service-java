package tokhttp3;

import java.io.Closeable;
import java.net.http.HttpResponse;
import java.util.List;

/**
 *
 * @author johan
 */
public class Response implements Closeable, AutoCloseable {

    private final HttpResponse httpResponse;
    
    public Response (HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }
    
    public ResponseBody body() {
        return new ResponseBody(httpResponse);
    }

    public int code() {
        return httpResponse.statusCode();
    }

    @Override
    public void close() {
        
    }

    public String header(String header) {
       return httpResponse.headers().firstValue(header).orElse(null);
    }

    public boolean isSuccessful() {
        int sc = httpResponse.statusCode();
        return ((sc >=200) && (sc < 300)) ;
    }

    public String message() {
        return "HTTP STATUS CODE = " + httpResponse.statusCode();
    }

    public List<String> headers(String setCookie) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
    
}
