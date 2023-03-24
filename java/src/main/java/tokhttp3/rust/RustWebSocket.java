package tokhttp3.rust;

import okio.ByteString;
import tokhttp3.WebSocket;

public class RustWebSocket implements WebSocket {

	@Override
	public boolean close(int i, String ok) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'close'");
	}

	@Override
	public boolean send(ByteString of) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'send'");
	}

	@Override
	public boolean send(String text) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'send'");
	}

	@Override
	public long queueSize() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("Unimplemented method 'queueSize'");
	}
}
