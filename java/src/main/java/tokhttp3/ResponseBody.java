package tokhttp3;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 *
 * @author johan
 * @param <T>
 */
public final class ResponseBody<T> implements Closeable {

    private final HttpResponse<T> httpResponse;
    long contentLength = -1;
    private static final Logger LOG = Logger.getLogger(ResponseBody.class.getName());

    public ResponseBody(HttpResponse<T> httpResponse) {
        this.httpResponse = httpResponse;
        HttpHeaders headers = httpResponse.headers();
        if (headers != null) {
            headers.firstValue("content-length")
                    .ifPresent(ls -> this.contentLength = Integer.parseInt(ls));
        }
    }

    public String string() throws IOException {
        T body = httpResponse.body();
        if (body == null) {
            LOG.info("null body, return empty string as response");
            return "";
        }
        if (body instanceof String) {
            return (String) body;
        }
        if (body instanceof byte[] bb) {
            return new String(bb);
        }
        throw new IOException("Not supported yet: " + body.getClass());
    } 

    public byte[] bytes() throws IOException {
        T body = httpResponse.body();
        if (body == null) return new byte[0];
        if (body instanceof byte[]) {
            return (byte[]) body;
        }
        if (body instanceof String) {
            return ((String) body).getBytes();
        }
        System.err.println("Unsupported body classL: "+body.getClass());
        throw new IOException("Not supported yet.");
    }

    public long contentLength() {
        if (contentLength > -1) {
            return contentLength;
        }
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
        if (body instanceof byte[]) {
            return new ByteArrayInputStream((byte[]) body);
        } else if (body instanceof String) {
            return new ByteArrayInputStream(((String) body).getBytes());
        }
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void close() {
        LOG.info("Closing responseBody for " + this);
    }

}
