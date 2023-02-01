package tokhttp3;

import java.io.ByteArrayInputStream;
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
    long contentLength = -1;
    
    public ResponseBody(HttpResponse<T> httpResponse) {
        this.httpResponse = httpResponse;
        httpResponse.headers().firstValue("content-length")
                .ifPresent(ls -> this.contentLength = Integer.parseInt(ls));
        System.err.println("Parsed content-length, set to "+contentLength);
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
        System.err.println("need to get bytes for class " + body.getClass());
        System.err.println("and body = "+body);
        if (body instanceof MyBodyHandler) {
            MyBodyHandler mbh = (MyBodyHandler)body;
            if (mbh.binary) {
            }
        }
        if (body instanceof byte[]) {
            return (byte[])body;
        }
        if (body instanceof String) {
            return ((String) body).getBytes();
        }
        throw new IOException("Not supported yet.");
    }

    public long contentLength() {
        if (contentLength > -1) return contentLength;
        T body = httpResponse.body();
        if (body instanceof byte[]) {
            return ((byte[]) body).length;
        } else {
            if (body instanceof String) {
                return ((String) body).getBytes().length;
            }
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public InputStream byteStream() {
        T body = httpResponse.body();
        System.err.println("bytestream asked, body of class "+body.getClass());
        if (body instanceof byte[]) {
            return new ByteArrayInputStream((byte[]) body);
        } else if (body instanceof String) {
            return new ByteArrayInputStream(((String) body).getBytes());
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void close() {
        System.err.println("Closing responseBody for "+this);
    }

}
