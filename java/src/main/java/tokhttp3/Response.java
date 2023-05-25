package tokhttp3;

import java.io.Closeable;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Optional;
import org.signal.libsignal.grpc.SignalRpcReply;

/**
 *
 * @author johan
 */
public class Response implements Closeable, AutoCloseable {

    private final HttpResponse httpResponse;
    
    public Response (HttpResponse httpResponse) {
        this.httpResponse = httpResponse;
    }
    
    public Response (SignalRpcReply grpcReply) {
        httpResponse = new HttpResponse(){
            @Override
            public int statusCode() {
                return grpcReply.getStatusCode();
            }

            @Override
            public HttpRequest request() {
                throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
            }

            @Override
            public Optional previousResponse() {
                throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
            }

            @Override
            public HttpHeaders headers() {
                return null;
            }

            @Override
            public Object body() {
                return grpcReply.getMessage();
            }

            @Override
            public Optional sslSession() {
                throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
            }

            @Override
            public URI uri() {
                throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
            }

            @Override
            public HttpClient.Version version() {
                throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
            }
        };
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
       if (httpResponse.headers() == null) return null;
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
