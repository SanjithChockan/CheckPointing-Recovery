import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicIntegerArray;

public class LocalState {

    /*
     * contents of checkpoint:
     * (a) the sequence number of the checkpointing protocol
     * (b) the current value of its vector clock
     * (c) any other information you may deem to be necessary for application’s
     * recovery
     */

    int sequenceNumber;
    AtomicIntegerArray vectorClock;

    ConcurrentHashMap<Integer, Integer> sendLabels;
    ConcurrentHashMap<Integer, Integer> FLS;
    ConcurrentHashMap<Integer, Integer> LLR;

    public LocalState(ConcurrentHashMap<Integer, Integer> sendLabels, ConcurrentHashMap<Integer, Integer> FLS,
            ConcurrentHashMap<Integer, Integer> LLR, AtomicIntegerArray vectorClock) {
        this.sendLabels = sendLabels;
        this.LLR = LLR;
        this.FLS = FLS;
        this.vectorClock = vectorClock;
    }

    public String toString() {
        return vectorClock.toString();
    }
}
