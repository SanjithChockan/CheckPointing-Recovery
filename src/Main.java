import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class Main {
    static int numOfNodes;
    static int interRequestDelay;
    static int csExecutionTime;
    static int numOfRequests;
    static ArrayList<Node> nodes = new ArrayList<>();
    static ArrayList<Node> neighbors = new ArrayList<>();
    static AtomicInteger numOfKeys = new AtomicInteger(0);

    // 1 represents node holds key between pair of a process
    // 0 represents does not have key
    static AtomicIntegerArray sharedKeys;

    static int currentNodeID;
    static Node currentNode;

    public static void main(String[] args) throws Exception {
        String fileName = "/home/010/s/sx/sxc180101/AdvancedOS/project2/config.txt";
        currentNodeID = Integer.parseInt(args[0]);
        readFile(fileName);
        sharedKeys = new AtomicIntegerArray(numOfNodes);
        distributeKeys();
        printInfo(Integer.parseInt(args[0]));
        SCTPClient client = new SCTPClient(neighbors);
        SCTPServer server = new SCTPServer(currentNode.port);

        // start servers and clients
        new Thread(server).start();
        Thread.sleep(5000);
        Thread.sleep(10000);

    }

    public static void printInfo(int machineNum) {
        System.out.println("Printing from machine: " + machineNum);
        System.out.println("Neighbors: " + neighbors.toString());
        System.out.println(nodes.toString());
        System.out.println(sharedKeys.toString());
    }

    public static void distributeKeys() {

        int toDistribute = currentNodeID;

        int iter = 0;
        while (toDistribute != 0) {
            sharedKeys.addAndGet(iter, 1);
            numOfKeys.incrementAndGet();
            iter++;
            toDistribute--;
        }
    }

    public static void readFile(String fileName) throws FileNotFoundException {
        File fileObj = new File(fileName);
        Scanner myReader = new Scanner(fileObj);
        int count = 0;
        while (myReader.hasNextLine()) {
            String data = myReader.nextLine();

            if (data.startsWith("#") || data.isEmpty()) {
                continue;
            }

            if (count == 0) {
                String[] dataArr = data.split(" ");
                numOfNodes = Integer.parseInt(dataArr[0]);
                interRequestDelay = Integer.parseInt(dataArr[1]);
                csExecutionTime = Integer.parseInt(dataArr[2]);
                numOfRequests = Integer.parseInt(dataArr[3]);
                count++;
            }

            else if (count == 1) {
                String[] dataArr = data.split(" ");
                int id = Integer.parseInt(dataArr[0]);
                String hostName = dataArr[1];
                int port = Integer.parseInt(dataArr[2]);
                nodes.add(new Node(id, hostName, port));
                if (currentNodeID != id) {
                    neighbors.add(new Node(id, hostName, port));
                } else {
                    currentNode = new Node(id, hostName, port);
                }

            }
        }
        myReader.close();
    }
}
