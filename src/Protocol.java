
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

public class Protocol {

    Node currentNode;
    HashMap<Integer, Integer> FLS;
    HashMap<Integer, Integer> LLR;

    public Protocol(Node currentNode) {
        this.currentNode = currentNode;
        initializeFLS();
        initializeLLR();
    }

    public void initializeFLS() {
        for (Node neighbor: currentNode.neighbors) {
            FLS.put(neighbor.ID, Integer.MIN_VALUE);
        }
    }

    public void initializeLLR() {
        for (Node neighbor: currentNode.neighbors) {
            FLS.put(neighbor.ID, Integer.MIN_VALUE);
        }
    }

    /*
     * CHECKPOINT ALGORITHM
     * - Take tentative checkpoint
     * - Request checkpoints to its cohorts
     * - Wait for yes from all cohorts
     * - If yes, make checkpoints permanent
     * - else discard tentative checkpoint
     */

}
