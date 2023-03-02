package tokhttp3;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import static java.net.http.HttpRequest.BodyPublishers.noBody;
import java.net.http.HttpResponse;
import java.util.HexFormat;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Request {

    private HttpRequest httpRequest;
    private final boolean ws;
    private URI origUri;

    private static final Logger LOG = Logger.getLogger(Request.class.getName());
    static final long SVB_LIFE = 1000 * 60 * 30; // 30 minutes
    static long lastSvb = 0l;
    static String echString;

    static {
        try {
            echString = getEchString().orElse("");
        } catch (Exception ex) {
            ex.printStackTrace();
            Logger.getLogger(MyCall.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    static void checkEch() {
        if ((System.currentTimeMillis() - lastSvb) > SVB_LIFE) {
            LOG.info("We need to get a new ECHConfig");
            getEchString().ifPresent(eh -> {
                echString = eh;
            });
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
            System.err.println("WAVEPROP = "+System.getProperty("wave.ech")+" and host = "+origHost);
            if (System.getProperty("wave.ech", "false").equalsIgnoreCase("true")) {
                if (origHost.equals("chat.gluonhq.net")) {
                    builder.header("innerSNI", "chat.gluonhq.net");
                    builder.header("outerSNI", "cloudflare-esni.com");
                    builder.header("echConfig", echString);
                    LOG.info("Request tuned for ECH, uri = " + this.origUri);
                }
            }
            HttpRequest httpRequest = builder.build();
            LOG.info("Final request = " + httpRequest.hashCode()+", ws = "+ws);
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
        String cmd = "dig +short  -t TYPE65 crypto.cloudflare.com"; // mac
        if (System.getProperty("os.name").equalsIgnoreCase("linux")) {
            cmd = "dig +unknownformat +short  -t TYPE65 crypto.cloudflare.com";
        }
        Process exec = Runtime.getRuntime().exec(cmd);
        BufferedReader br = new BufferedReader(new InputStreamReader(
                exec.getInputStream()));
        exec.waitFor();
        String res = br.readLine();
        String answer = res.substring(7).replaceAll(" ", ""); 
        return answer; 
    }

    public static String getSvbFromDNS()  {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .build();
            String outer = "cloudflare-ech.com";
            String hidden = "signal7.gluonhq.net";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://1.1.1.1/dns-query?name=crypto.cloudflare.com&type=65"))
                    .header("accept", "application/dns-json")
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String r1 = response.body();
            LOG.info("HTTPS RR = "+r1);

            int i1 = r1.indexOf("\"Answer\"");
            String r2 = r1.substring(i1);

            int i2 = r2.indexOf("\"data\"");
            String r3 = r2.substring(i2).replaceAll(" ", "").toUpperCase();
            LOG.info("svb = "+r3);

            return r3;
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, null, ex);
        } catch (InterruptedException ex) {
            LOG.log(Level.SEVERE, null, ex);
        }
        return "";
    }

    public static Optional<String> getEchString(){
        String echString = getSvbFromDNS();
        int s1 = echString.indexOf("FE0D");
        int start = s1 - 4; // first 4 octects length
        int len = 2 + HexFormat.fromHexDigits(echString, start, s1);
        String answer = echString.substring(start, start + 2 * len);
        return Optional.of(answer);
    }
}
