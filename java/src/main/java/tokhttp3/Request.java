package tokhttp3;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import static java.net.http.HttpRequest.BodyPublishers.noBody;
import java.util.HexFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Request {

    private HttpRequest httpRequest;
    private final boolean ws;
    private URI origUri;

    private static final Logger LOG = Logger.getLogger(Request.class.getName());
    static String echString;

    static {
        try {
            echString = getEchString();
        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.getLogger(MyCall.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    Request(HttpRequest httpRequest, boolean ws, URI origUri) {
        this.httpRequest = httpRequest;
        this.ws = ws;
        this.origUri = origUri;
    }

    public HttpRequest getHttpRequest() {
        return this.httpRequest;
    }

    public HttpUrl url() {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    public URI getUri() {
        return origUri;
    }

    public Builder newBuilder() {
        return new Request.Builder();
    }

    public static class Builder {

        HttpRequest.Builder builder;
        boolean ws = false;
        URI origUri;

        public Builder() {
            this.builder = HttpRequest.newBuilder();
        }

        public Request build() {
            String origHost = this.origUri.getHost();
            if (System.getProperty("wave.ech", "false").equalsIgnoreCase("true")) {
                if (origHost.equals("chat.gluonhq.net")) {
                    builder.header("innerSNI", "chat.gluonhq.net");
                    builder.header("outerSNI", "cloudflare-esni.com");
                    builder.header("echConfig", echString);
                    LOG.info("Request tuned for ECH, uri = " + this.origUri);
                }
            }
            HttpRequest httpRequest = builder.build();
            LOG.info("Final request = " + httpRequest.uri()+", ws = "+ws);
            return new Request(httpRequest, ws, origUri);
        }

        public Builder url(HttpUrl url) {
            this.origUri = url.getUri();
            builder.uri(url.getUri());
            return this;
        }
        
        public Builder url(String format) {
            try {
                URI uri = new URI(format);
                this.origUri = uri;
                if (uri.getScheme().toLowerCase().equals("wss")) {
                    ws = true;
                    uri = new URI("https://"+format.substring(6));
                } 
                builder.uri(uri);
            } catch (URISyntaxException |IllegalArgumentException ex) {
                Logger.getLogger(Request.class.getName()).log(Level.SEVERE, null, ex);
            }
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
            builder.method(method, body == null ? noBody() : body.getBodyPublisher());
            if (body != null) {
                LOG.info("add content type to "+body.contentType.getMediaType());
                builder.header("Content-Type", body.contentType.getMediaType());
            }
            return this;
        }

    }
    public static String getSvb() throws Exception {
        String cmd = "dig +unknownformat +short  -t TYPE65 crypto.cloudflare.com";
        Process exec = Runtime.getRuntime().exec(cmd);
        BufferedReader br = new BufferedReader(new InputStreamReader(
                exec.getInputStream()));
        exec.waitFor();
        String res = br.readLine();
        String answer = res.substring(7).replaceAll(" ", ""); 
        return answer; 
    }

    public static String getEchString() throws Exception {
        String echString = getSvb();
        int s1 = echString.indexOf("FE0D");
        int start = s1 - 4; // first 4 octects length
        int len = 2 + HexFormat.fromHexDigits(echString, start, s1);
        String answer = echString.substring(start, start + 2 * len);
        return answer;
    }
}
