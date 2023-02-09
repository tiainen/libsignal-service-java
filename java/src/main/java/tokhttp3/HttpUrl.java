/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package tokhttp3;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author johan
 */
public final class HttpUrl {

    private final URI uri;
    
    public HttpUrl(URI uri){
        this.uri = uri;
    }  

    public HttpUrl(URL url) throws URISyntaxException {
        this.uri = url.toURI();
    }
    
    public URI getUri() {
        return this.uri;
    }

    public String scheme() {
        return uri.getScheme();
    }
    
    public String host() {
        return uri.getHost();
    }
    
    public int port() {
        return uri.getPort();
    }
    
    public String encodedPath() {
        return uri.getPath();
    }
    
    public String encodedFragment() {
        return uri.getFragment();
    }

    
    public String encodedQuery() {
        return uri.getRawQuery();
    }

    public static HttpUrl get(String urlString) {
        try {
            URI uri = new URI(urlString);
            return new HttpUrl(uri);
        } catch (URISyntaxException ex) {
            Logger.getLogger(HttpUrl.class.getName()).log(Level.SEVERE, null, ex);
            throw new IllegalArgumentException(ex);
        }
    }

    public HttpUrl resolve(String location) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    public static class Builder {
        
        private String scheme;
        private String host;
        private int port;
        private String encodedPath;
        
        public Builder() {
        }

        public Builder scheme(String scheme) {
            this.scheme = scheme;
            return this;
        }

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder encodedPath(String encodedPath) {
            this.encodedPath = encodedPath;
            return this;
        }

        public Builder addEncodedPathSegments(String substring) {
            this.encodedPath = this.encodedPath+"/"+substring;
            return this;
        }

        public Builder encodedQuery(String encodedQuery) {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        public Builder encodedFragment(String encodedFragment) {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

        public HttpUrl build() {
            throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
        }

    }
    
}
