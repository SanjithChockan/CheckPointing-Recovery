
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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class Protocol {

    int minDelay;
    int actionIndex = 0;

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

    // variables for checkpointing
    AtomicInteger sentCommit = new AtomicInteger();
    AtomicBoolean acknowledgementReceived = new AtomicBoolean(Boolean.FALSE);
    AtomicInteger sentRequests = new AtomicInteger();
    AtomicBoolean awaitResult = new AtomicBoolean(Boolean.TRUE);
    AtomicBoolean willingToCheckPoint = new AtomicBoolean(Boolean.TRUE);
    AtomicBoolean receivedCommitDecision = new AtomicBoolean(Boolean.FALSE);
    AtomicBoolean makeCheckpointPermanent = new AtomicBoolean(Boolean.FALSE);
    AtomicBoolean instanceInProgress = new AtomicBoolean(Boolean.TRUE);
    AtomicBoolean hasTakenTentativeCk = new AtomicBoolean(Boolean.FALSE);
    AtomicBoolean alreadyReceivedToCommit = new AtomicBoolean(Boolean.FALSE);

    // variables to move on to next operation
    AtomicBoolean alreadyReceivedMoveOnMessage = new AtomicBoolean(Boolean.FALSE);
    AtomicBoolean receivedMoveOnMessage = new AtomicBoolean(Boolean.FALSE);

    // add local state to list everytime you make a perm checkpoint
    ArrayList<LocalState> permCheckpoints = new ArrayList<LocalState>();
    LocalState tentativeCheckpoint;

    public Protocol(Node currentNode, ArrayList<Action> operations, int minDelay) throws Exception {
        this.minDelay = minDelay;
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
        Thread.sleep(5000);
    }

    public void startProtcol() {
        // iterate through operations (checkpointing/recovery)

        int iter = 0;
        while (iter < operations.size()) {

            Action op = operations.get(iter);
            if (op.initiator.ID == currentNode.ID) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }

                if (op.mode.equals("c")) {
                    System.out.println("Checkpoint initiated by: " + currentNode.ID);
                    instanceInProgress.set(true);
                    new Thread(new Checkpoint(currentNode.ID, new HashSet<Integer>())).start();
                    while (instanceInProgress.get()) {
                    }
                    ;

                    // inform other processes that your are done with your instance using flooding
                    HashSet<Integer> parents = new HashSet<Integer>();
                    parents.add(currentNode.ID);
                    for (Integer i : currentNode.neighbors.keySet()) {
                        try {
                            client.sendMessage(currentNode.neighbors.get(i), new Message(MessageType.MOVE_ON,
                                    "move to next action", currentNode.ID, -1, parents));
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }

                    iter += 1;
                    System.out.println("Moving on to next operation");
                }
            }

            else {
                // wait until current instance is done before moving on to next operation
                while (!receivedMoveOnMessage.get()) {
                }
                ;
                iter += 1;
                receivedMoveOnMessage.set(false);

            }
        }
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
            synchronized (LLR) {
                LLR.put(msg.NodeID, msg.piggyback_LLR);
            }

        }

        else if (msg.msgType == MessageType.TAKE_TENTATIVE_CK) {

            System.out.println("Received to take tentative ck from machine " + msg.NodeID);
            if (!hasTakenTentativeCk.get()) {
                System.out.println("msg.piggyback_LLR = " + msg.piggyback_LLR);
                System.out.println("FLS.get(msg.NodeID) = " + FLS.get(msg.NodeID));
                // if LLR >= FLS > ground
                if ((msg.piggyback_LLR >= FLS.get(msg.NodeID)) && (FLS.get(msg.NodeID) > Integer.MIN_VALUE)) {
                    System.out.println("Taking tentative ck");
                    hasTakenTentativeCk.set(true);
                    new Thread(new Checkpoint(msg.NodeID, msg.parents)).start();
                } else {
                    System.out.println("not taking tentative ck");
                    // send willing_to_ck
                    try {
                        client.sendMessage(currentNode.neighbors.get(msg.NodeID),
                                new Message(MessageType.WILLING_TO_CK, "not required to take ck", currentNode.ID, 0,
                                        null));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            } else {
                System.out.println("Already taken tentative checkpoint");
                try {
                    client.sendMessage(currentNode.neighbors.get(msg.NodeID),
                            new Message(MessageType.WILLING_TO_CK, "already took ck", currentNode.ID, 0,
                                    null));
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

        }

        // process willing_to_ck: "yes" or "no" (true or false) to checkpoint message
        else if (msg.msgType == MessageType.WILLING_TO_CK) {

            System.out.println("Received Willing to take Checkpoint from machine " + msg.NodeID);
            willingToCheckPoint.set(willingToCheckPoint.get() && Boolean.TRUE);
            sentRequests.decrementAndGet();

            if (sentRequests.get() == 0) {
                awaitResult.set(false);
            }
        }

        else if (msg.msgType == MessageType.NOT_WILLING_TO_CK) {
            willingToCheckPoint.set(Boolean.FALSE);
        }

        // process commit message
        else if (msg.msgType == MessageType.COMMIT) {

            HashSet<Integer> parents = msg.parents;
            parents.add(currentNode.ID);

            if (!receivedCommitDecision.get()) {
                receivedCommitDecision.set(true);
                System.out.println("Received message to make checkpoint permanent");
                // make tentative checkpoint permanent
                if (tentativeCheckpoint != null) {
                    System.out.println("Making checkpoint permanent since I took local checkpoint");
                    permCheckpoints.add(tentativeCheckpoint);
                    hasTakenTentativeCk.set(false);
                    receivedCommitDecision.set(true);
                    tentativeCheckpoint = null;
                }

                else {
                    System.out.println("Did not take tentative ck - no permanent checkpoint");
                }

                // flood commit message to neighbors

                int localSentCommitMessages = 0;
                for (Integer nei : currentNode.neighbors.keySet()) {
                    if (!parents.contains(nei)) {
                        try {
                            localSentCommitMessages++;
                            sentCommit.incrementAndGet();
                            System.out.println("SENDING MESSAGE TO COMMIT TO MACHINE " + nei);
                            client.sendMessage(currentNode.neighbors.get(nei),
                                    new Message(MessageType.COMMIT, "", currentNode.ID, 0, parents));
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }

                final int lscm = localSentCommitMessages;
                new Thread(new Runnable() {
                    public void run() {
                        // wait for ackowledgement
                        if (lscm > 0) {
                            while (!acknowledgementReceived.get()) {
                            }
                        }
                        System.out.println("RECEIVED ACKLOWDGEMENT FROM ALL NEIGHBORS");

                        // send acknowledge message to parent
                        try {
                            System.out.println("SENDING ACKOWLEDGEMENT MESSAGE TO MACHINE " + msg.NodeID);
                            client.sendMessage(currentNode.neighbors.get(msg.NodeID),
                                    new Message(MessageType.ACKNOWLEDGE, null, currentNode.ID, 0, parents));
                        } catch (Exception e) {

                        }
                    }
                }).start();

            } else {
                try {
                    System.out.println("SENDING ACKOWLEDGEMENT MESSAGE TO MACHINE " + msg.NodeID);
                    client.sendMessage(currentNode.neighbors.get(msg.NodeID),
                            new Message(MessageType.ACKNOWLEDGE, null, currentNode.ID, 0, parents));
                } catch (Exception e) {

                }
            }
        }

        // only for initiator to end its current instance
        else if (msg.msgType == MessageType.ACKNOWLEDGE) {
            System.out.println("RECEIVED ACKNOWLEDGEMENT FROM MACHINE " + msg.NodeID);
            sentCommit.decrementAndGet();
            if (sentCommit.get() == 0) {
                acknowledgementReceived.set(true);
                System.out.println("MADE ALL CHECKPOINTS PERMANENT");
            }
        }

        // process move on to next operation message
        else if (msg.msgType == MessageType.MOVE_ON) {

            if (!alreadyReceivedMoveOnMessage.get()) {
                alreadyReceivedMoveOnMessage.set(true);
                System.out.println("Received Message to move on to next operation");
                HashSet<Integer> parents = msg.parents;
                parents.add(currentNode.ID);

                // Flood message to neighbors
                for (Integer nei : currentNode.neighbors.keySet()) {
                    if (!parents.contains(nei)) {
                        try {
                            client.sendMessage(currentNode.neighbors.get(nei),
                                    new Message(MessageType.MOVE_ON, "", currentNode.ID, 0, parents));
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }
                }
                receivedMoveOnMessage.set(Boolean.TRUE);
            }
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
        HashSet<Integer> parents;

        public Checkpoint(int initiator, HashSet<Integer> parents) {
            this.initiator = initiator;
            this.parents = parents;
            this.parents.add(initiator);
            tentativeCheckpoint = null;
            receivedCommitDecision.set(false);
            acknowledgementReceived.set(false);
            awaitResult.set(true);
            alreadyReceivedMoveOnMessage.set(false);
        }

        @Override
        public void run() {
            // TODO Auto-generated method stub
            sendMessageLock.lock();
            System.out.println("Starting checkpoint");
            try {
                takeTentativeCK();
                instanceInProgress.set(false);
                hasTakenTentativeCk.set(false);
                // tentativeCheckpoint = null;
            } finally {
                sendMessageLock.unlock();
            }
            System.out.println("Completed checkpoint");
        }

        public void takeTentativeCK() {

            System.out.println("At takeTentativeCK() method");

            // take local checkpoint
            synchronized (LLR) {

                ConcurrentHashMap<Integer, Integer> LLRCopy = new ConcurrentHashMap<Integer, Integer>();
                ConcurrentHashMap<Integer, Integer> FLSCopy = new ConcurrentHashMap<Integer, Integer>();
                ConcurrentHashMap<Integer, Integer> sendLabelsCopy = new ConcurrentHashMap<Integer, Integer>();
                LLRCopy.putAll(LLR);
                FLSCopy.putAll(FLS);
                sendLabelsCopy.putAll(sendLabels);

                tentativeCheckpoint = new LocalState(sendLabelsCopy, FLSCopy, LLRCopy);

                cohorts = new ArrayList<Integer>();

                // get cohorts and set LLR & FLS to ground
                // only send to cohorts that are not parents (initiators)

                for (Integer k : LLR.keySet()) {
                    if (LLR.get(k) != Integer.MIN_VALUE) {
                        cohorts.add(k);
                        LLR.put(k, Integer.MIN_VALUE);
                    }
                }
            }

            boolean willing_to_ck = sendRequestToCohorts();
            // send willing_to_ck to initiator

            if (initiator != currentNode.ID) {
                try {
                    System.out.println("Sending parent WILLING_TO_CK");
                    if (willing_to_ck) {
                        client.sendMessage(currentNode.neighbors.get(initiator),
                                new Message(MessageType.WILLING_TO_CK, "null", currentNode.ID, 0, null));
                    } else {
                        client.sendMessage(currentNode.neighbors.get(initiator),
                                new Message(MessageType.NOT_WILLING_TO_CK, "null", currentNode.ID, 0, null));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                // wait for either commit or don't commit message
                while (!receivedCommitDecision.get()) {
                }
                ;

            } else {
                // if initiator, commit checkpoint if willing_to_ck == true
                if (willing_to_ck) {
                    commitCheckpoints();
                } else {
                    // discard checkpoints
                }
            }
        }

        public boolean sendRequestToCohorts() {
            System.out.println("at sendRequestToCohorts()");
            // request cohorts to take checkpoint, don't send to initator (add logic)
            int localNumSentToCohorts = 0;
            for (Integer c : cohorts) {
                if (!parents.contains(c)) {
                    try {
                        sentRequests.incrementAndGet();
                        System.out.println("Sending request to machine " + c + " to take a tentative checkpoint");
                        localNumSentToCohorts++;
                        client.sendMessage(currentNode.neighbors.get(c), new Message(MessageType.TAKE_TENTATIVE_CK,
                                "requesting to take tentative checkpoint", currentNode.ID,
                                tentativeCheckpoint.LLR.get(c), parents));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

            System.out.println("Sent requests to cohorts, waiting for decision from neighbors");

            // await until you receive decision
            if (localNumSentToCohorts > 0) {
                System.out.println("Awaiting decisions from cohorts");
                while (awaitResult.get()) {
                }
                ;
                System.out.println("Received all decisions from cohorts");
            }
            return willingToCheckPoint.get();
        }

        public void commitCheckpoints() {
            System.out.println("At commit checkpoints");
            // make checkpoint permanent & send commit message to all cohorts that took
            permCheckpoints.add(tentativeCheckpoint);

            // send all neighbors (flood) to commit if they have taken a tentative
            // checkpoint
            for (Integer c : currentNode.neighbors.keySet()) {
                if (!parents.contains(c)) {
                    try {
                        System.out.println("Sending eligible cohorts to make their checkpoints permanent");
                        sentCommit.incrementAndGet();
                        client.sendMessage(currentNode.neighbors.get(c), new Message(MessageType.COMMIT,
                                "requesting to make checkpoint permanent", currentNode.ID,
                                tentativeCheckpoint.LLR.get(c), parents));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

            // wait for neighbors to acknowledge completion of instance; every should have
            // taken permanent checkpoint before exiting initiator instance
            while (!acknowledgementReceived.get()) {
            }

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
            List<Integer> keysAsArray = new ArrayList<Integer>(currentNode.neighbors.keySet());
            Random rand = new Random();

            while (true) {
                // pick random neighbor and send message
                sendMessageLock.lock();

                try {
                    Node randomNeighbor = currentNode.neighbors.get(keysAsArray.get(rand.nextInt(keysAsArray.size())));
                    try {
                        int newLabelValue = sendLabels.get(randomNeighbor.ID) + 1;
                        // set FLS if no message has been sent since last checkpoint
                        if (FLS.get(randomNeighbor.ID) == Integer.MIN_VALUE) {
                            FLS.put(randomNeighbor.ID, newLabelValue);
                        }
                        sendLabels.put(randomNeighbor.ID, newLabelValue);
                        client.sendMessage(randomNeighbor,
                                new Message(MessageType.APPLICATION, "sending app message", currentNode.ID,
                                        newLabelValue, null));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                } finally {
                    sendMessageLock.unlock();
                }
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }

        }
    }
}
