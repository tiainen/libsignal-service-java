/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package tokhttp3;

import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscribers;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/**
 *
 * @author johan
 */
public class MyBodyHandler implements HttpResponse.BodyHandler {

    boolean text = false;
    boolean binary = false;
    @Override
    public HttpResponse.BodySubscriber apply(HttpResponse.ResponseInfo responseInfo) {
        HttpResponse.BodySubscriber answer;
        String ct = responseInfo.headers().firstValue("Content-Type").orElse("");
        System.err.println("CT in response = "+ct);
        if (ct.indexOf("json") > -1) {
            answer = BodySubscribers.ofString(UTF_8);
            text = true;
        } else {
            answer = BodySubscribers.ofInputStream();
            binary = true;
        }
        return answer;
    }
    
}
