# Koo and Toueg Checkpointing and Recovery protocol

Prevents the well known "domino effect" as well as livelock problems associated with rollback-recovery.

## Distributed System Implementation:

    Consists of n nodes numbered from 0 to n - 1:
        - Reliable socket connections implemented using either TCP or SCTP between pair of nodes corresponding to provided topology, and all messages are exchanged over these connections. All connections are established in the beginning and stay intact until the program ends.
        - Each node randomly waits for a period of time that is exponentially probability distributed before generating a request

## Algorithm Implementation

    Any node can initiate an instance of checkpointing or recovery. However, at most one instance of checkpointing/recovery protocol will be in progress at any time.

    Each instance of checkpointing protocol will have a sequence number associated with it. A node, when taking a checkpoint, stores:
        - the sequence number of the checkpointing protocol
        - the current value of its vector clock

## Configuration File:

     The first valid line of the configuration file contains two tokens. 
        - The first token is the number of nodes in the system.
        - The second token is the value of minDelay

    Next n lines consist of three tokens.
        - The first token is the node ID.
        - The second token is the host-name of the machine on which the node runs.
        - The third token is the port on which the node listens for incoming connections.
    
    The next n lines consist of a space delimited list of at most n − 1 tokens.
        - The kth valid line after the first line is a space delimited list of node IDs which are the neighbor of node k.
    
    The subsequent lines until the end of file will contain the tuples indicating the checkpointing or recovery operation needed to simulate

## To run the program:
    - Run launcher.sh with config file
    - After program ends, run test_consistency.py to ensure correctness.
        - Ensures latest set of checkpoints are consistent/concurrent using vector clock protocol
        - Ensures system rolls back to consistent state during recovery
    - run cleanup.sh to kill all processes