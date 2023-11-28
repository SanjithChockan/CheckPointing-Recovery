import java.io.Serializable;
import java.nio.ByteBuffer;
import java.io.ObjectOutputStream;
import java.io.ObjectInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

// Enumeration to store message types
enum MessageType {
	APPLICATION, TAKE_TENTATIVE_CK, WILLING_TO_CK, NOT_WILLING_TO_CK, COMMIT, RECOVER, ROLLBACK
};

// Object to store message passing between nodes
// Message class can be modified to incoroporate all fields than need to be
// passed
// Message needs to be serializable
// Most base classes and arrays are serializable
public class Message implements Serializable {
	MessageType msgType;
	public String message;
	int NodeID;
	int piggyback_LLR;

	// Constructor
	public Message(MessageType msgType, String message, int NodeID, int piggyback_LLR) {
		this.msgType = msgType;
		this.message = message;
		this.NodeID = NodeID;
		this.piggyback_LLR = piggyback_LLR;
	}

	// Convert current instance of Message to ByteBuffer in order to send message
	// over SCTP
	public ByteBuffer toByteBuffer() throws Exception {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream oos = new ObjectOutputStream(bos);
		oos.writeObject(this);
		oos.flush();

		ByteBuffer buf = ByteBuffer.allocateDirect(bos.size());
		buf.put(bos.toByteArray());

		oos.close();
		bos.close();

		// Buffer needs to be flipped after writing
		// Buffer flip should happen only once
		buf.flip();
		return buf;
	}

	// Retrieve Message from ByteBuffer received from SCTP
	public static Message fromByteBuffer(ByteBuffer buf) throws Exception {
		// Buffer needs to be flipped before reading
		// Buffer flip should happen only once
		buf.flip();
		byte[] data = new byte[buf.limit()];
		buf.get(data);
		buf.clear();

		ByteArrayInputStream bis = new ByteArrayInputStream(data);
		ObjectInputStream ois = new ObjectInputStream(bis);
		Message msg = (Message) ois.readObject();

		bis.close();
		ois.close();

		return msg;
	}

}
