package tokhttp3;

import io.privacyresearch.worknet.NetRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HexFormat;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import okio.Buffer;

public class Request {

    private static final Logger LOG = Logger.getLogger(Request.class.getName());

    private final NetRequest netRequest;
    private final HttpUrl url;

    Request(NetRequest netRequest, URI uri) {
        this.netRequest = netRequest;
        this.url = new HttpUrl(uri);
    }

    public HttpUrl url() {
        return url;
    }

    public NetRequest getNetRequest() {
        return this.netRequest;
    }

    public Builder newBuilder() {
        return new Request.Builder();
    }

    public static class Builder {

        NetRequest.Builder builder;
        URI uri;

        public Builder() {
            this.builder = NetRequest.newBuilder();
        }

        public Request build() {
            if ("chat.gluonhq.net".equals(this.uri.getHost())) {
                builder.echEnabled(true);
            }

            NetRequest netRequest = builder.build();
            LOG.info("Final request = " + netRequest.hashCode());
            return new Request(netRequest, uri);
        }

        public Builder url(HttpUrl httpUrl) {
            this.uri = httpUrl.getUri();
            builder.uri(this.uri);
            return this;
        }

        public Builder url(String url) {
            try {
                URI finalUri = new URI(url);
                this.uri = finalUri;
                if ("wss".equals(uri.getScheme())) {
                    try {
                        finalUri = new URI("https://" + url.substring(6));
                    } catch (URISyntaxException | IllegalArgumentException ex) {
                        LOG.log(Level.SEVERE, null, ex);
                    }
                }
                builder.uri(finalUri);
            } catch (URISyntaxException | IllegalArgumentException ex) {
                LOG.log(Level.SEVERE, null, ex);
            }
            return this;
        }

        public Builder uri(URI uri) {
            this.uri = uri;
            builder.uri(this.uri);
            return this;
        }

        public Builder header(String key, String encodeBytes) {
            builder.header(key, encodeBytes);
            return this;
        }

        public Builder addHeader(String key, String encodeBytes) {
            builder.header(key, encodeBytes);
            return this;
        }

        public Builder put(RequestBody requestBody) {
            return method("PUT", requestBody);
        }

        public Builder get() {
            return method("GET", null);
        }

        public Builder post(RequestBody requestBody) {
            return method("POST", requestBody);
        }

        public Builder method(String method, RequestBody body) {
            builder.method(method);
            if (body != null) {
                try {
                    Buffer buffer = new Buffer();
                    body.writeTo(buffer);
                    builder.body(buffer.readByteArray());

                    LOG.info("add content type to "+body.contentType().getMediaType());
                    builder.header("Content-Type", body.contentType().getMediaType());
                } catch (IOException ex) {
                    LOG.log(Level.SEVERE, null, ex);
                }
            }
            return this;
        }
    }
}
