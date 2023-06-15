package org.whispersystems.signalservice.api.websocket;


import com.gluonhq.snl.Response;

public interface ConnectivityListener {
  void onConnected();
  void onConnecting();
  void onDisconnected();
  void onAuthenticationFailure();
  boolean onGenericFailure(Response response, Throwable throwable);
  void onError();
}
