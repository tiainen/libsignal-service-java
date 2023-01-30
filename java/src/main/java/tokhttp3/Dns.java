package tokhttp3;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

public interface Dns {
      List<InetAddress> lookup(String hostname) throws UnknownHostException;

  Dns SYSTEM = new Dns() {
    @Override public List<InetAddress> lookup(String hostname) throws UnknownHostException {
      if (hostname == null) throw new UnknownHostException("hostname == null");
      return Arrays.asList(InetAddress.getAllByName(hostname));
    }
  };
}
