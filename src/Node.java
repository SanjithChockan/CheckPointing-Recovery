import java.util.ArrayList;
import java.util.HashMap;

public class Node {

    int ID;
    String hostName;
    int port;
    //ArrayList<Node> neighbors = new ArrayList<Node>();
    HashMap<Integer, Node> neighbors = new HashMap<Integer, Node>();

    public Node(int nodeID, String hostName, int port) {
        this.ID = nodeID;
        this.hostName = hostName;
        this.port = port;
    }

    public void addNeighbor(Node neighbor) {
        neighbors.put(neighbor.ID, neighbor);
    }

    public String toString() {
        return ID + " " + hostName + " " + port;
    }

}
