/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package tokhttp3;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import okio.ByteString;

/**
 *
 * @author johan
 */
public class TokWebSocket implements WebSocket {
    private static final Logger LOG = Logger.getLogger(TokWebSocket.class.getName());

    private final WebSocketListener twsl;
    private java.net.http.WebSocket jws;
    
    public TokWebSocket(WebSocketListener twsl) {
        this.twsl = twsl;
    }
    
    void setJavaWebSocket(java.net.http.WebSocket jw) {
        this.jws = jw;
    }

    @Override
    public boolean close(int i, String ok) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public boolean send(ByteString of) {
        LOG.info("SENDBINARY text to "+this.jws+" and state = "+this.jws.isInputClosed()+", "+this.jws.isOutputClosed());
        if (this.jws.isOutputClosed()) {
            twsl.onClosed(this, 0, "Output closed without reason");
        }
        CompletableFuture<java.net.http.WebSocket> cf = this.jws.sendBinary(of.asByteBuffer(), true);
        try {
            java.net.http.WebSocket wwss = cf.get();
        } catch (InterruptedException | ExecutionException ex) {
            Logger.getLogger(TokWebSocket.class.getName()).log(Level.SEVERE, null, ex);
        }
        LOG.info("Data has been sent to "+this.jws);
        return true;
    }

    @Override
    public boolean send(String text) {
        LOG.info("SENTEXT text to "+this.jws+" and state = "+this.jws.isInputClosed()+", "+this.jws.isOutputClosed());
        this.jws.sendText(text, false);
        return true;
    }

    @Override
    public long queueSize() {
        return 1;
    }
    
}
