public class Node {

    int nodeID;
    String hostName;
    int port;

    public Node(int nodeID, String hostName, int port) {
        this.nodeID = nodeID;
        this.hostName = hostName;
        this.port = port;
    }

    public String toString() {
        return nodeID + " " + hostName + " " + port;
    }
}
