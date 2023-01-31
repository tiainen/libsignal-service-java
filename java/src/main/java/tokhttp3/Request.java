/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package tokhttp3;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import static java.net.http.HttpRequest.BodyPublishers.noBody;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author johan
 */
public class Request {

    private HttpRequest httpRequest;
    private final boolean ws;
    private URI origUri;

    Request(HttpRequest httpRequest, boolean ws, URI origUri) {
        this.httpRequest = httpRequest;
        this.ws = ws;
        this.origUri = origUri;
    }

    public HttpRequest getHttpRequest() {
        return this.httpRequest;
    }

    public HttpUrl url() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    public URI getUri() {
        return origUri;
//        return this.httpRequest.uri();
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
            HttpRequest httpRequest = builder.build();
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
            System.err.println("HEADER "+key+" with val " + encodeBytes);
            builder.header(key, encodeBytes);
            return this;
        }

        public Builder addHeader(String key, String encodeBytes) {
            System.err.println("ADDHEADER "+key+" with val " + encodeBytes);
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
                builder.header("Content-Type", body.contentType.getMediaType());
            }
            return this;
        }

    }

}
