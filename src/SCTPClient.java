import com.sun.nio.sctp.*;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;

class SCTPClient {
	// PORT to connect to server
	HashMap<Integer, Node> neighbors;
	int MAX_MSG_SIZE = 4096;
	HashMap<Integer, SctpChannel> channels;

	public SCTPClient(HashMap<Integer, Node> neighbors) {
		this.neighbors = neighbors;
		channels = new HashMap<>();
	}

	public void initiateChannels() throws Exception {
		for (Integer neighbor : neighbors.keySet()) {
			Node nei = neighbors.get(neighbor);
			InetSocketAddress addr = new InetSocketAddress(nei.hostName, nei.port);

			System.out.println("Trying connection to server");
			Thread.sleep(3000);
			SctpChannel sc = SctpChannel.open(addr, 0, 0); // Connect to server using the address
			channels.put(nei.ID, sc);
			System.out.println("Connected to Server " + addr.getHostName());
		}
	}

	public void sendMessage(Node destination, Message msg) throws Exception {
		MessageInfo messageInfo = MessageInfo.createOutgoing(null, 0); // MessageInfo for SCTP layer
		SctpChannel sc = channels.get(destination.ID);
		sc.send(msg.toByteBuffer(), messageInfo); // Messages are sent over SCTP using ByteBuffer
	}

}
