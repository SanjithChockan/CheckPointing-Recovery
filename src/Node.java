import java.util.ArrayList;

public class Node {

    int ID;
    String hostName;
    int port;
    ArrayList<Node> neighbors = new ArrayList<Node>();

    public Node(int nodeID, String hostName, int port) {
        this.ID = nodeID;
        this.hostName = hostName;
        this.port = port;
    }

    public void addNeighbor(Node neighbor) {
        neighbors.add(neighbor);
    }

    public String toString() {
        return ID + " " + hostName + " " + port;
    }

}
