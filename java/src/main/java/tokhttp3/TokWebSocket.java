/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package tokhttp3;

import okio.ByteString;

/**
 *
 * @author johan
 */
public class TokWebSocket implements WebSocket {

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
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public boolean send(String text) {
        this.jws.sendText(text, false);
        return true;
    }

    @Override
    public long queueSize() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
    
}
