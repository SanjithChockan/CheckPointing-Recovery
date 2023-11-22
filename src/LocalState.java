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

    public LocalState() {

    }
}
