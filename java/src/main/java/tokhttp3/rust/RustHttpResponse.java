package tokhttp3.rust;

import java.net.URI;
import java.net.http.HttpClient.Version;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Optional;

import javax.net.ssl.SSLSession;

public class RustHttpResponse implements HttpResponse<byte[]> {

    private HttpRequest request;
    private RustResponse response;
    private URI uri;

    public RustHttpResponse(HttpRequest request, RustResponse response) {
        this.request = request;
        this.uri = request.uri();
        this.response = response;
    }

    @Override
    public int statusCode() {
        return response.getStatusCode();
    }

    @Override
    public HttpRequest request() {
        return request;
    }

    @Override
    public Optional<HttpResponse<byte[]>> previousResponse() {
        return Optional.empty();
    }

    @Override
    public HttpHeaders headers() {
        return HttpHeaders.of(Map.of(), (a, b) -> true);
    }

    @Override
    public byte[] body() {
        return response.getBody();
    }

    @Override
    public Optional<SSLSession> sslSession() {
        return Optional.empty();
    }

    @Override
    public URI uri() {
        return uri;
    }

    @Override
    public Version version() {
        return response.isHttp2() ? Version.HTTP_2 : Version.HTTP_1_1; 
    }
}
