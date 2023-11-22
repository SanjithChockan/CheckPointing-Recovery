
/*
 * Following class implements checkpointing and recovery service
 * Things to implement:
 * - Freezing send events after taking tentative checkpoint
 * - Checkpointing
 * - Recovery
 * - Flooding
 * 
 */

import java.util.HashMap;
import java.util.concurrent.locks.ReentrantLock;

public class Protocol {

    Node currentNode;
    HashMap<Integer, Integer> FLS;
    HashMap<Integer, Integer> LLR;
    ReentrantLock sendMessageLock = new ReentrantLock(Boolean.TRUE);

    public Protocol(Node currentNode) {
        this.currentNode = currentNode;
        initializeFLS();
        initializeLLR();
    }

    public void initializeFLS() {
        for (Node neighbor : currentNode.neighbors) {
            FLS.put(neighbor.ID, Integer.MIN_VALUE);
        }
    }

    public void initializeLLR() {
        for (Node neighbor : currentNode.neighbors) {
            LLR.put(neighbor.ID, Integer.MIN_VALUE);
        }
    }

    public void receiveMessages() {
        
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

        @Override
        public void run() {
            // TODO Auto-generated method stub
            takeTentativeCK();
        }

        public void takeTentativeCK() {
            // take local checkpoint
            // then send request to cohorts
        }

        public void sendRequestToCohorts() {

            // await until you receive decision

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

        }
    }
}
