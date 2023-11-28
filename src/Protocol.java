
/*
 * Following class implements checkpointing and recovery service
 * Things to implement:
 * - Freezing send events after taking tentative checkpoint
 * - Checkpointing
 * - Recovery
 * - Flooding
 * 
 */

import java.util.ArrayList;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class Protocol {

    SCTPServer server;
    SCTPClient client;
    Node currentNode;
    ArrayList<Action> operations;
    ConcurrentHashMap<Integer, Integer> FLS = new ConcurrentHashMap<Integer, Integer>();
    ConcurrentHashMap<Integer, Integer> LLR = new ConcurrentHashMap<Integer, Integer>();
    ConcurrentHashMap<Integer, Integer> sendLabels = new ConcurrentHashMap<Integer, Integer>();
    ReentrantLock sendMessageLock = new ReentrantLock(Boolean.TRUE);

    public Protocol(Node currentNode, ArrayList<Action> operations) throws Exception {
        this.currentNode = currentNode;
        this.operations = operations;
        this.server = new SCTPServer(currentNode.port);
        this.client = new SCTPClient(this.currentNode.neighbors);
        initialize();
        initialize();

        // start server and clients
        new Thread(server).start();
        Thread.sleep(5000);
        startClients();
        Thread.sleep(5000);
    }

    public void startProtcol() {
        // iterate through operations (checkpointing/recovery)
    }

    public void initialize() {
        for (Integer neighbor : currentNode.neighbors.keySet()) {
            Node nei = currentNode.neighbors.get(neighbor);
            FLS.put(nei.ID, Integer.MIN_VALUE);
            LLR.put(nei.ID, Integer.MIN_VALUE);
            sendLabels.put(nei.ID, 0);
        }
    }

    public void startClients() throws Exception {
        client.initiateChannels();
    }

    public void processReceivedMessage(Message msg) {

    }

    /*
     * CHECKPOINT ALGORITHM
     * To send messages (application, ck, etc.) have different threads and only send
     * when a lock is aquired
     * Will help freeze application message until lock is given up by checkpointing
     * thread
     * - Take tentative checkpoint
     * - Request checkpoints to its cohorts
     * - Wait for yes from all cohorts
     * - If yes, make checkpoints permanent
     * - else discard tentative checkpoint
     */

    class Checkpoint implements Runnable {

        ArrayList<Integer> cohorts;
        @Override
        public void run() {
            // TODO Auto-generated method stub
            sendMessageLock.lock();
            try {
                takeTentativeCK();
            } finally {
                sendMessageLock.unlock();
            }
        }

        public void takeTentativeCK() {
            LocalState tentativeCheckpoint;
            // take local checkpoint
            synchronized (LLR) {
                tentativeCheckpoint = new LocalState(sendLabels, FLS, LLR);
                cohorts = new ArrayList<Integer>();
                
                // get cohorts and set LLR & FLS to ground
                for (Integer k: LLR.keySet()) {
                    if (LLR.get(k) != Integer.MIN_VALUE) {
                        cohorts.add(k);
                        LLR.put(k, Integer.MIN_VALUE);
                    }
                }
            }
            
            boolean commit = sendRequestToCohorts(tentativeCheckpoint, cohorts);

            // commit after receving responses
            if (commit) {
                commitCheckpoints();
            }
        }

        public boolean sendRequestToCohorts(LocalState ck, ArrayList<Integer> cohorts) {

            // request cohorts to take checkpoint
            for (Integer c: cohorts) {
                try {
                    client.sendMessage(currentNode.neighbors.get(c), new Message(MessageType.TENTATIVE_CK, "requesting to take tentative checkpoint", currentNode.ID));
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            
            // await until you receive decision

            return false;
        }

        public void commitCheckpoints() {

        }
    }

    // this will keep running to send APP messages
    // only way it will freeze is when current process obtains sendMessage lock to
    // take tentative checkpoint
    class Application implements Runnable {

        @Override
        public void run() {
            // TODO Auto-generated method stub
            sendApplicationMessages();
        }

        public void sendApplicationMessages() {
            Random rand = new Random();

            while (true) {
                // pick random neighbor and send message
                sendMessageLock.lock();
                try {
                    Node randomNeighbor = currentNode.neighbors.get(rand.nextInt(currentNode.neighbors.size()));
                    try {
                        int newLabelValue = sendLabels.get(randomNeighbor.ID) + 1;
                        // set FLS if no message has been sent since last checkpoint
                        if (FLS.get(randomNeighbor.ID) == Integer.MIN_VALUE) {
                            FLS.put(randomNeighbor.ID, newLabelValue);
                        }
                        sendLabels.put(randomNeighbor.ID, newLabelValue);
                        client.sendMessage(randomNeighbor,
                                new Message(MessageType.APPLICATION, "sending app message", currentNode.ID));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                } finally {
                    sendMessageLock.unlock();
                }

            }

        }
    }
}
