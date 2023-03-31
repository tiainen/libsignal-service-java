package tokhttp3;

import io.privacyresearch.worknet.NetResponse;
import java.io.Closeable;
import java.util.List;

/**
 *
 * @author johan
 */
public class Response implements Closeable, AutoCloseable {

    private final NetResponse netResponse;

    public Response(NetResponse netResponse) {
        this.netResponse = netResponse;
    }

    public ResponseBody body() {
        return new ResponseBody(netResponse);
    }

    public int code() {
        return netResponse.getStatusCode();
    }

    @Override
    public void close() {
    }

    public String header(String header) {
        return netResponse.getHeaders().get(header);
    }

    public boolean isSuccessful() {
        int statusCode = netResponse.getStatusCode();
        return statusCode >= 200 && statusCode < 300;
    }

    public String message() {
        return "HTTP STATUS CODE = " + netResponse.getStatusCode();
    }

    public List<String> headers(String setCookie) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
