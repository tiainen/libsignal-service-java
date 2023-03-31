package tokhttp3.rust;

import java.util.List;
import java.util.Map;

import io.privacyresearch.worknet.Client;
import io.privacyresearch.worknet.NetListener;
import io.privacyresearch.worknet.NetRequest;
import io.privacyresearch.worknet.WebSocketNetResponse;
import okio.ByteString;
import tokhttp3.Response;
import tokhttp3.WebSocket;
import tokhttp3.WebSocketListener;

public class RustWebSocket implements WebSocket {

	private Client client;
	private long webSocketPointer;
	private WebSocketListener listener;

	public RustWebSocket(WebSocketListener listener) {
		this.client = new Client();
		this.listener = listener;
	}

	public void connect(NetRequest request) {
		Thread connectThread = new Thread(() -> {
			WebSocketNetResponse webSocketNetResponse = client.wsOpen(request);
			if (webSocketNetResponse != null && webSocketNetResponse.getWebSocketPointer() != 0) {
				this.webSocketPointer = webSocketNetResponse.getWebSocketPointer();
				this.listener.onOpen(this, new Response(webSocketNetResponse));

				client.wsRead(this.webSocketPointer, this::onData, this::onText);
			}
		}, "RustWebSocket-Connect");
		connectThread.start();
	}

	private void onData(byte[] data) {
		this.listener.onMessage(this, ByteString.of(data));
	}

	private void onText(String text) {
		this.listener.onMessage(this, text);
	}

	@Override
	public boolean close(int i, String ok) {
		return true;
	}

	@Override
	public boolean send(ByteString of) {
		return this.client.wsWrite(this.webSocketPointer, of.toByteArray()) == 1;
	}

	@Override
	public boolean send(String text) {
		return this.client.wsWrite(this.webSocketPointer, text) == 1;
	}

	@Override
	public long queueSize() {
		return 1L;
	}
}
