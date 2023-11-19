public class Action {
    Node initiator;
    String mode;

    public Action(Node initiator, String mode) {
        this.initiator = initiator;
        this.mode = mode;
    }

    @Override
    public String toString() {
        return initiator.ID + ": " + mode; 
    }
}
