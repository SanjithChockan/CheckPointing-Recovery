
/*
 * Following class implements checkpointing and recovery service
 * Things to implement:
 * - Freezing send events after taking tentative checkpoint
 * - Checkpointing
 * - Recovery
 * - Flooding
 * 
 */

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.concurrent.locks.ReentrantLock;

public class Protocol {

    int minDelay;
    int actionIndex = 0;

    SCTPServer server;
    SCTPClient client;
    Node currentNode;
    ArrayList<Action> operations;
    AtomicIntegerArray vectorClock;
    int nodeSize;
    Application app = new Application();
    ConcurrentHashMap<Integer, Integer> FLS = new ConcurrentHashMap<Integer, Integer>();
    ConcurrentHashMap<Integer, Integer> LLR = new ConcurrentHashMap<Integer, Integer>();
    ConcurrentHashMap<Integer, Integer> LLS = new ConcurrentHashMap<Integer, Integer>();
    ConcurrentHashMap<Integer, Integer> sendLabels = new ConcurrentHashMap<Integer, Integer>();

    // output checkpoints to a file
    FileWriter fileWriter;
    BufferedWriter outputWriter;

    // if initiator, don't need to check LLR >= FLS > ground
    // AtomicBoolean initiator = new AtomicBoolean(Boolean.TRUE);

    // variables for checkpointing
    AtomicInteger globalSequence = new AtomicInteger(0);
    ReentrantLock sendMessageLock = new ReentrantLock(Boolean.TRUE);
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

    // vriables for recovery
    AtomicBoolean willingToRecover = new AtomicBoolean(Boolean.TRUE);
    AtomicBoolean hasRolledBack = new AtomicBoolean(Boolean.FALSE);
    AtomicBoolean receivedRecoveryResponses = new AtomicBoolean(Boolean.FALSE);
    AtomicBoolean alreadyResetSystem = new AtomicBoolean(Boolean.FALSE);

    // variables to move on to next operation
    AtomicBoolean alreadyReceivedMoveOnMessage = new AtomicBoolean(Boolean.FALSE);
    AtomicBoolean receivedMoveOnMessage = new AtomicBoolean(Boolean.FALSE);

    // add local state to list everytime you make a perm checkpoint
    ArrayList<LocalState> permCheckpoints = new ArrayList<LocalState>();
    LocalState tentativeCheckpoint;

    public Protocol(Node currentNode, ArrayList<Action> operations, int minDelay, int nodeSize) throws Exception {
        this.minDelay = minDelay;
        this.currentNode = currentNode;
        this.operations = operations;
        this.server = new SCTPServer(currentNode.port, this);
        this.client = new SCTPClient(this.currentNode.neighbors);
        this.nodeSize = nodeSize;
        this.vectorClock = new AtomicIntegerArray(this.nodeSize);
        initialize();

        try {
            fileWriter = new FileWriter(
                    "/home/010/s/sx/sxc180101/AdvancedOS/project3/node-" + this.currentNode.ID
                            + "-checkpoints.out");
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        outputWriter = new BufferedWriter(fileWriter);

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
            globalSequence.set(iter);
            Action op = operations.get(iter);
            if (op.initiator.ID == currentNode.ID) {
                try {
                    Thread.sleep(5000);
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
                            // vectorClock.incrementAndGet(currentNode.ID);
                            client.sendMessage(currentNode.neighbors.get(i), new Message(MessageType.MOVE_ON,
                                    "move to next action", currentNode.ID, -1, 0, parents, vectorClock));
                        } catch (Exception e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                    }

                    iter += 1;
                    System.out.println("Moving on to next operation");
                } else if (op.mode.equals("r")) {
                    // each process must roll back no more than once if necessary
                    System.out.println("Rollback initiated by: " + currentNode.ID);
                    instanceInProgress.set(true);
                    new Thread(new Recovery(currentNode.ID, new HashSet<Integer>())).start();
                }
            } else {
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
            LLS.put(nei.ID, Integer.MIN_VALUE);
            sendLabels.put(nei.ID, 0);
        }
    }

    public void startClients() throws Exception {
        client.initiateChannels();
    }

    public void processReceivedMessage(Message msg) {

        // process application message
        if (msg.msgType == MessageType.APPLICATION) {
            // received a message; updated vector clock of process sent from
            synchronized (vectorClock) {
                for (int i = 0; i < nodeSize; i++) {
                    vectorClock.set(i, Math.max(msg.vectorClock.get(i), vectorClock.get(i)));
                }
                vectorClock.incrementAndGet(currentNode.ID);
            }
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
                        // vectorClock.incrementAndGet(currentNode.ID);
                        client.sendMessage(currentNode.neighbors.get(msg.NodeID),
                                new Message(MessageType.WILLING_TO_CK, "not required to take ck", currentNode.ID, 0, 0,
                                        null, vectorClock));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            } else {
                System.out.println("Already taken tentative checkpoint");
                try {
                    // vectorClock.incrementAndGet(currentNode.ID);
                    client.sendMessage(currentNode.neighbors.get(msg.NodeID),
                            new Message(MessageType.WILLING_TO_CK, "already took ck", currentNode.ID, 0, 0,
                                    null, vectorClock));
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

            if (tentativeCheckpoint != null) {
                System.out.println("Made checkpoint permanent");
                permCheckpoints.add(tentativeCheckpoint);
                try {
                    outputWriter.write("Sequence: " + globalSequence + " Vector Clock: "
                            + tentativeCheckpoint.vectorClock.toString());
                    outputWriter.newLine();
                    outputWriter.flush();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                // inform its cohorts to make their tentative checkpoints permanent
                for (Integer c : tentativeCheckpoint.LLR.keySet()) {
                    if (!parents.contains(c)) {
                        if (tentativeCheckpoint.LLR.get(c) != Integer.MIN_VALUE) {
                            try {
                                client.sendMessage(currentNode.neighbors.get(c),
                                        new Message(MessageType.COMMIT, "", currentNode.ID, 0, 0, parents,
                                                vectorClock));
                            } catch (Exception e) {
                                // TODO Auto-generated catch block
                                e.printStackTrace();
                            }
                        }
                    }
                }
                tentativeCheckpoint = null;
                receivedCommitDecision.set(true);
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

        else if (msg.msgType == MessageType.RECOVER) {
            System.out.println("Received to rollback from machine " + msg.NodeID);

            // rollback if LLR > LLS
            if (!hasRolledBack.get()) {
                if (LLR.get(msg.NodeID) < msg.piggyback_LLS) {
                    // agree to roll back
                    System.out.println("agree to roll back");
                    hasRolledBack.set(true);
                    new Thread(new Recovery(msg.NodeID, msg.parents));
                } else {
                    System.out.println("no need to roll back");
                    // send willing_to_ck
                    try {
                        client.sendMessage(currentNode.neighbors.get(msg.NodeID),
                                new Message(MessageType.WILLING_TO_RB, "not required to roll back", currentNode.ID, 0,
                                        0,
                                        null, vectorClock));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            } else {
                System.out.println("Already agreed to rollback");
                try {
                    // vectorClock.incrementAndGet(currentNode.ID);
                    client.sendMessage(currentNode.neighbors.get(msg.NodeID),
                            new Message(MessageType.WILLING_TO_RB, "already took ck", currentNode.ID, 0, 0,
                                    null, vectorClock));
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        } else if (msg.msgType == MessageType.ROLLBACK) {

            if (hasRolledBack.get()) {

                if (!alreadyResetSystem.get()) {
                    // set new system state
                    synchronized (vectorClock) {
                        LocalState lastCheckpoint = permCheckpoints.remove(permCheckpoints.size() - 1);
                        sendLabels.putAll(lastCheckpoint.sendLabels);
                        FLS.putAll(lastCheckpoint.FLS);
                        LLR.putAll(lastCheckpoint.LLR);
                        LLS.putAll(lastCheckpoint.LLS);

                        for (int i = 0; i < nodeSize; i++) {
                            vectorClock.set(i, tentativeCheckpoint.vectorClock.get(i));
                        }
                    }
                    alreadyResetSystem.set(true);
                }

            }
            // send roll back to neighbors
            HashSet<Integer> parents = msg.parents;
            parents.add(currentNode.ID);

            for (Integer nei : currentNode.neighbors.keySet()) {
                if (!parents.contains(nei)) {
                    try {
                        client.sendMessage(currentNode.neighbors.get(nei), new Message(MessageType.ROLLBACK, null, currentNode.ID, 0, 0, parents, vectorClock));
                    } catch (Exception e) {

                    }
                }
            }

        }

        else if (msg.msgType == MessageType.WILLING_TO_RB) {
            System.out.println("Received Willing to take Checkpoint from machine " + msg.NodeID);
            willingToRecover.set(willingToRecover.get() && Boolean.TRUE);
            sentRequests.decrementAndGet();

            if (sentRequests.get() == 0) {
                receivedRecoveryResponses.set(true);
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
                            // vectorClock.incrementAndGet(currentNode.ID);
                            client.sendMessage(currentNode.neighbors.get(nei),
                                    new Message(MessageType.MOVE_ON, "", currentNode.ID, 0, 0, parents, vectorClock));
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

            try {
                System.out.println("Acquiring lock");
                sendMessageLock.lock();
                System.out.println("Starting checkpoint");
                takeTentativeCK();
                instanceInProgress.set(false);
                hasTakenTentativeCk.set(false);
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
                ConcurrentHashMap<Integer, Integer> LLSCopy = new ConcurrentHashMap<Integer, Integer>();
                ConcurrentHashMap<Integer, Integer> sendLabelsCopy = new ConcurrentHashMap<Integer, Integer>();
                LLRCopy.putAll(LLR);
                FLSCopy.putAll(FLS);
                LLSCopy.putAll(LLS);
                sendLabelsCopy.putAll(sendLabels);

                AtomicIntegerArray vectorClockCopy = new AtomicIntegerArray(nodeSize);

                synchronized (vectorClock) {
                    vectorClock.incrementAndGet(currentNode.ID);
                    for (int i = 0; i < nodeSize; i++) {
                        vectorClockCopy.set(i, vectorClock.get(i));
                    }
                }
                tentativeCheckpoint = new LocalState(sendLabelsCopy, FLSCopy, LLRCopy, LLSCopy, vectorClockCopy);

                cohorts = new ArrayList<Integer>();

                // get cohorts and set LLR & FLS to ground
                // only send to cohorts that are not parents (initiators) & not ground (LLR)

                for (Integer k : LLR.keySet()) {
                    if (LLR.get(k) != Integer.MIN_VALUE) {
                        cohorts.add(k);
                    }

                    LLR.put(k, Integer.MIN_VALUE);
                    FLS.put(k, Integer.MIN_VALUE);
                }
            }

            boolean willing_to_ck = sendRequestToCohorts();
            // send willing_to_ck to initiator

            if (initiator != currentNode.ID) {
                try {
                    System.out.println("Sending parent WILLING_TO_CK");
                    if (willing_to_ck) {
                        // vectorClock.incrementAndGet(currentNode.ID);
                        client.sendMessage(currentNode.neighbors.get(initiator),
                                new Message(MessageType.WILLING_TO_CK, "null", currentNode.ID, 0, 0, null,
                                        vectorClock));
                    } else {
                        // vectorClock.incrementAndGet(currentNode.ID);
                        client.sendMessage(currentNode.neighbors.get(initiator),
                                new Message(MessageType.NOT_WILLING_TO_CK, "null", currentNode.ID, 0, 0, null,
                                        vectorClock));
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
                        // vectorClock.incrementAndGet(currentNode.ID);
                        client.sendMessage(currentNode.neighbors.get(c), new Message(MessageType.TAKE_TENTATIVE_CK,
                                "requesting to take tentative checkpoint", currentNode.ID,
                                tentativeCheckpoint.LLR.get(c), 0, parents, vectorClock));
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
            // write ck to file with instance sequence
            try {
                outputWriter.write(
                        "Sequence: " + globalSequence + " Vector Clock: " + tentativeCheckpoint.vectorClock.toString());
                outputWriter.newLine();
                outputWriter.flush();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            // send to processes that took tentative checkpoint
            for (Integer c : cohorts) {
                if (!parents.contains(c)) {
                    try {
                        System.out.println("Sending cohort" + c + "to make its checkpoint permanent");
                        sentCommit.incrementAndGet();
                        // vectorClock.incrementAndGet(currentNode.ID);
                        client.sendMessage(currentNode.neighbors.get(c), new Message(MessageType.COMMIT,
                                "requesting to make checkpoint permanent", currentNode.ID,
                                tentativeCheckpoint.LLR.get(c), 0, parents, vectorClock));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    class Recovery implements Runnable {

        int initiator;
        HashSet<Integer> parents;

        public Recovery(int initiator, HashSet<Integer> parents) {
            this.initiator = initiator;
            this.parents = parents;
            this.parents.add(initiator);
            receivedRecoveryResponses.set(false);
        }

        @Override
        public void run() {
            try {
                System.out.println("Acquiring lock");
                sendMessageLock.lock();
                // reseting system
                if (initiator == currentNode.ID) {
                    System.out.println("Failed process back to last permanent checkpoint");
                    resetSystemToLastCheckpoint();
                }
                System.out.println("Starting rollback");
                rollback();
                instanceInProgress.set(false);
                hasRolledBack.set(false);
            } finally {
                sendMessageLock.unlock();
            }
            System.out.println("Completed rollback");
        }

        public void rollback() {
            // send prepare to rollback messages along with LLS

            for (Integer nei : currentNode.neighbors.keySet()) {
                if (!parents.contains(nei)) {
                    try {
                        sentRequests.incrementAndGet();
                        client.sendMessage(currentNode.neighbors.get(nei), new Message(MessageType.RECOVER,
                                "prepare to rollback", currentNode.ID, 0, LLS.get(nei), null, vectorClock));
                    } catch (Exception e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

            // await from all processes to receive willing_to_recover
            System.out.println("Awaiting responses from neighbors");
            while (!receivedRecoveryResponses.get()) {
            }
            ;
            System.out.println("Received all responses from neighbors");

            if (initiator == currentNode.ID) {

                // if willing_to_recover = true, send rollback to all processes
                if (willingToRecover.get()) {
                    // send permanent rollback to neighbors
                    for (Integer nei : currentNode.neighbors.keySet()) {
                        if (!parents.contains(nei)) {
                            try {
                                client.sendMessage(currentNode.neighbors.get(nei), new Message(MessageType.ROLLBACK,
                                        "rollback if you can", currentNode.ID, 0, 0, parents, vectorClock));
                            } catch (Exception e) {

                            }
                        }
                    }
                }

            } else {
                try {
                    System.out.println("Sending parent WILLING_TO_CK");
                    if (willingToRecover.get()) {
                        // vectorClock.incrementAndGet(currentNode.ID);
                        client.sendMessage(currentNode.neighbors.get(initiator),
                                new Message(MessageType.WILLING_TO_RB, "null", currentNode.ID, 0, 0, null,
                                        vectorClock));
                    } else {
                        // vectorClock.incrementAndGet(currentNode.ID);
                        client.sendMessage(currentNode.neighbors.get(initiator),
                                new Message(MessageType.NOT_WILLING_TO_RECOVER, "null", currentNode.ID, 0, 0, null,
                                        vectorClock));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }

            }

        }

        public void resetSystemToLastCheckpoint() {

            synchronized (vectorClock) {
                LocalState lastCheckpoint = permCheckpoints.remove(permCheckpoints.size() - 1);
                sendLabels.putAll(lastCheckpoint.sendLabels);
                FLS.putAll(lastCheckpoint.FLS);
                LLR.putAll(lastCheckpoint.LLR);
                LLS.putAll(lastCheckpoint.LLS);

                for (int i = 0; i < nodeSize; i++) {
                    vectorClock.set(i, tentativeCheckpoint.vectorClock.get(i));
                }
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

                try {
                    System.out.println("sending app messages");
                    // pick random neighbor and send message
                    sendMessageLock.lock();
                    Node randomNeighbor = currentNode.neighbors.get(keysAsArray.get(rand.nextInt(keysAsArray.size())));
                    try {
                        int newLabelValue = sendLabels.get(randomNeighbor.ID) + 1;
                        // set FLS if no message has been sent since last checkpoint
                        if (FLS.get(randomNeighbor.ID) == Integer.MIN_VALUE) {
                            FLS.put(randomNeighbor.ID, newLabelValue);
                        }

                        LLS.put(randomNeighbor.ID, newLabelValue);
                        sendLabels.put(randomNeighbor.ID, newLabelValue);

                        AtomicIntegerArray vectorClockCopy = new AtomicIntegerArray(nodeSize);

                        synchronized (vectorClock) {
                            vectorClock.incrementAndGet(currentNode.ID);

                            for (int i = 0; i < nodeSize; i++) {
                                vectorClockCopy.set(i, vectorClock.get(i));
                            }
                        }
                        client.sendMessage(randomNeighbor,
                                new Message(MessageType.APPLICATION, "sending app message", currentNode.ID,
                                        newLabelValue, 0, null, vectorClockCopy));
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
