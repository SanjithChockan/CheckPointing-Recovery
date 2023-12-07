import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

public class Main {
    static int numOfNodes;
    static int minDelay;
    static ArrayList<Node> nodes = new ArrayList<>();
    static int currentNodeID;
    static Node currentNode;

    static ArrayList<Action> operations = new ArrayList<Action>();

    public static void main(String[] args) throws Exception {
        String fileName = "/home/010/s/sx/sxc180101/AdvancedOS/project3/config.txt";
        currentNodeID = Integer.parseInt(args[0]);
        readFile(fileName);
        printInfo(Integer.parseInt(args[0]));

        Protocol protocolObj = new Protocol(currentNode, operations, minDelay, nodes.size());
        protocolObj.startProtcol();

    }

    public static void printInfo(int machineNum) {
        System.out.println("Printing from machine: " + machineNum);
        System.out.println("Neighbors: " + currentNode.neighbors);
        System.out.println("Sequence: " + operations.toString());
    }

    public static void readFile(String fileName) throws FileNotFoundException {
        File fileObj = new File(fileName);
        Scanner myReader = new Scanner(fileObj);
        int count = 0;
        int nodeCounter = 0;
        while (myReader.hasNextLine()) {
            String data = myReader.nextLine();

            if (data.startsWith("#") || data.isEmpty()) {
                continue;
            }

            data = data.split("#")[0].trim();

            // beginning of file
            if (count == 0) {
                String[] dataArr = data.split(" ");
                numOfNodes = Integer.parseInt(dataArr[0]);
                minDelay = Integer.parseInt(dataArr[1]);
                count += 1;
                continue;
            }

            // add nodes
            if (count == 1) {
                if (nodeCounter < numOfNodes) {
                    String[] dataArr = data.split(" ");
                    int id = Integer.parseInt(dataArr[0]);
                    String hostName = dataArr[1];
                    int port = Integer.parseInt(dataArr[2]);

                    if (currentNodeID == id) {
                        currentNode = new Node(id, hostName, port);
                        nodes.add(currentNode);
                    } else {
                        nodes.add(new Node(id, hostName, port));
                    }
                    nodeCounter += 1;

                } else {
                    nodeCounter = 0;
                    count += 1;
                }
            }

            // neighbors
            if (count == 2) {
                if (nodeCounter < numOfNodes) {
                    String[] strNeighbors = data.split(" ");
                    for (String nei: strNeighbors) {
                        nodes.get(nodeCounter).addNeighbor(nodes.get(Integer.parseInt(nei)));
                    }

                    nodeCounter += 1;
                } else {
                    nodeCounter = 0;
                    count += 1;
                }
            }

            if (count == 3) {
                String[] crLine = data.split(",");
                String mode = crLine[0].substring(1);
                int id = Integer.parseInt(crLine[1].substring(0, crLine[1].length()-1));
                operations.add(new Action(nodes.get(id), mode));
            }
        }
        myReader.close();
    }
}
