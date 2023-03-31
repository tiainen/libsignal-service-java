package tokhttp3;

import io.privacyresearch.worknet.NetResponse;
import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 *
 * @author johan
 */
public final class ResponseBody implements Closeable {

    private static final Logger LOG = Logger.getLogger(ResponseBody.class.getName());

    private final NetResponse netResponse;
    private long contentLength = -1;

    public ResponseBody(NetResponse netResponse) {
        this.netResponse = netResponse;
        String contentLengthHeader = this.netResponse.getHeaders().get("content-length");
        if (contentLengthHeader != null) {
            this.contentLength = Integer.parseInt(contentLengthHeader);
        }
    }

    public String string() throws IOException {
        byte[] body = netResponse.getBody();
        if (body == null) {
            LOG.info("null body, return empty string as response");
            return "";
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    public byte[] bytes() throws IOException {
        return netResponse.getBody();
    }

    public long contentLength() {
        if (contentLength > -1) {
            return contentLength;
        }
        byte[] body = netResponse.getBody();
        return body != null ? body.length : -1;
    }

    public InputStream byteStream() {
        byte[] body = netResponse.getBody();
        return body != null ? new ByteArrayInputStream(body) : null;
    }

    public void close() {
        LOG.info("Closing responseBody for " + this);
    }
}
