
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class Protocol {

    SCTPServer server;
    SCTPClient client;
    Node currentNode;
    ArrayList<Action> operations;
    Application app = new Application();
    ConcurrentHashMap<Integer, Integer> FLS = new ConcurrentHashMap<Integer, Integer>();
    ConcurrentHashMap<Integer, Integer> LLR = new ConcurrentHashMap<Integer, Integer>();
    ConcurrentHashMap<Integer, Integer> sendLabels = new ConcurrentHashMap<Integer, Integer>();
    ReentrantLock sendMessageLock = new ReentrantLock(Boolean.TRUE);

    // if initiator, don't need to check LLR >= FLS > ground
    AtomicBoolean initiator = new AtomicBoolean(Boolean.TRUE);

    AtomicInteger sentRequests = new AtomicInteger();
    AtomicBoolean awaitResult = new AtomicBoolean(Boolean.TRUE);
    AtomicBoolean willingToCheckPoint = new AtomicBoolean(Boolean.TRUE);

    // add local state to list everytime you make a perm checkpoint
    ArrayList<LocalState> permCheckpoints = new ArrayList<LocalState>();

    public Protocol(Node currentNode, ArrayList<Action> operations) throws Exception {
        this.currentNode = currentNode;
        this.operations = operations;
        this.server = new SCTPServer(currentNode.port, this);
        this.client = new SCTPClient(this.currentNode.neighbors);
        initialize();
        initialize();

        // start server and clients
        new Thread(server).start();
        Thread.sleep(5000);
        startClients();
        Thread.sleep(5000);
        new Thread(app).start();
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
        // process application message
        if (msg.msgType == MessageType.APPLICATION) {
            // increment LLR on corresponding process
            LLR.put(msg.NodeID, msg.piggyback_LLR);

        }

        else if (msg.msgType == MessageType.TAKE_TENTATIVE_CK) {
            // if LLR >= FLS > ground
            if (msg.piggyback_LLR >= FLS.get(msg.NodeID) && FLS.get(msg.NodeID) != Integer.MIN_VALUE) {
                new Thread(new Checkpoint(msg.NodeID)).start();
            } else {
                // send willing_to_ck
                try {
                    client.sendMessage(currentNode.neighbors.get(msg.NodeID),
                            new Message(MessageType.WILLING_TO_CK, "not required to take ck", currentNode.ID, 0));
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        // process willing_to_ck: "yes" or "no" (true or false) to checkpoint message
        else if (msg.msgType == MessageType.WILLING_TO_CK) {
            willingToCheckPoint.set(Boolean.TRUE);
        }

        else if (msg.msgType == MessageType.NOT_WILLING_TO_CK) {
            willingToCheckPoint.set(Boolean.FALSE);
        }

        // process commit message
        else if (msg.msgType == MessageType.COMMIT) {
            // make tentative checkpoint permanent
        }

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
        int initiator;

        public Checkpoint(int initiator) {
            this.initiator = initiator;
        }

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
                for (Integer k : LLR.keySet()) {
                    if (LLR.get(k) != Integer.MIN_VALUE) {
                        cohorts.add(k);
                        LLR.put(k, Integer.MIN_VALUE);
                    }
                }
            }

            boolean willing_to_ck = sendRequestToCohorts(tentativeCheckpoint, cohorts);
            // send willing_to_ck to initiator
            try {
                if (willing_to_ck) {
                    client.sendMessage(currentNode.neighbors.get(initiator),
                            new Message(MessageType.WILLING_TO_CK, "null", currentNode.ID, 0));
                } else {
                    client.sendMessage(currentNode.neighbors.get(initiator),
                            new Message(MessageType.NOT_WILLING_TO_CK, "null", currentNode.ID, 0));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // wait for commit message from intiator

        }

        public boolean sendRequestToCohorts(LocalState ck, ArrayList<Integer> cohorts) {

            // request cohorts to take checkpoint, don't send to initator (add logic)
            for (Integer c : cohorts) {
                try {
                    sentRequests.incrementAndGet();
                    client.sendMessage(currentNode.neighbors.get(c), new Message(MessageType.TAKE_TENTATIVE_CK,
                            "requesting to take tentative checkpoint", currentNode.ID, ck.LLR.get(c)));
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

            // await until you receive decision
            while (awaitResult.get()) {
                // once inside, received all decision from cohort
                awaitResult.set(Boolean.TRUE);
            }

            return willingToCheckPoint.get();
        }

        public void commitCheckpoints() {
            // send commit message to all cohorts that took tentative checkpoint
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
                                new Message(MessageType.APPLICATION, "sending app message", currentNode.ID, 0));
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
