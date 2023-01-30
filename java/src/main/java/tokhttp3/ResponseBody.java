package tokhttp3;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;

/**
 *
 * @author johan
 * @param <T>
 */
public final class ResponseBody<T> implements Closeable {

    private final HttpResponse<T> httpResponse;
    
    public ResponseBody(HttpResponse<T> httpResponse) {
        this.httpResponse = httpResponse;
    }
    
    public String string() throws IOException {
        T body = httpResponse.body();
        if (body instanceof String) {
            return (String)body;
        }
        throw new IOException("Not supported yet."); 
    }
    
    public byte[] bytes() throws IOException {
        T body = httpResponse.body();
        if (body instanceof byte[]) {
            return (byte[])body;
        }
        throw new IOException("Not supported yet.");
    }

    public long contentLength() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public InputStream byteStream() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void close() {
        System.err.println("Closing responseBody for "+this);
    }

}
