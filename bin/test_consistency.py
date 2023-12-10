import sys
import ast
import numpy as np

num_of_nodes = int(sys.argv[1])

checkpoint_sequences = [0, 1, 3]

recovery_instances = [2, 4]


states = []

for i in range(num_of_nodes):
    states.append([])
    ci = 0
    with open("node-" + str(i) + "-checkpoints.out", "r") as file:
        for line in file:
            data = (line.rstrip()).split(":")
            if not data:
                continue
            sequence_id = int(data[0])
            vector_clock = ast.literal_eval(data[1])
            cps_i = checkpoint_sequences[ci]
            
            if cps_i == sequence_id:
                states[i].append(vector_clock)
            else:
                if len(states[i]) == 0:
                    states[i].append([0]*num_of_nodes)
                else:
                    states[i].append(states[i][len(states[i])-1])
            ci += 1
            
# for each checkpoint, check if it is consistent with the rest
np_states = np.array(states)
result = "latest set of checkpoints are concurrent"

def is_concurrent(v1, v2):

    # check if v1 < v2 than
    less_than = True
    for vn1, vn2 in zip(v1, v2):
        if vn1 > vn2:
            less_than = False

    if (less_than):
        return False
    
    # check if v1 > v2 than
    more_than = True
    for vn1, vn2 in zip(v1, v2):
        if vn1 < vn2:
            more_than = False

    if (more_than):
        return False
    return True

# checkpoint consistency check
for k in range(len(checkpoint_sequences)):
    checkpoint_set = (np_states[:, k]).tolist()
    # check each pair is concurrent
    for i in range(len(checkpoint_set)):
        for j in range(i+1, len(checkpoint_set)):

            if not(is_concurrent(checkpoint_set[i], checkpoint_set[j])):
                result = "inconsistent"
                break
        
        if result == "inconsistent":
            break
print(result)


# recovery roll back to consistent checkpoint check
# check for received messages by a process that doesn't have knowledge of sending it
recovery_sequences = {}

for i in range(num_of_nodes):
    
    with open("node-" + str(i) + "-recovery.out", "r") as file:
        for line in file:
            data = (line.rstrip()).split(":")
            if not data:
                continue

            sequence_id = int(data[0])
            if sequence_id not in recovery_sequences:
                recovery_sequences[sequence_id] = []


            sent_messages = ast.literal_eval(data[2])
            received_messages = ast.literal_eval(data[4])

            item_to_insert = (sent_messages, received_messages)
            recovery_sequences[sequence_id].append(item_to_insert)
    
print(recovery_sequences)

for r in recovery_sequences:
    recoveries = recovery_sequences[r]
    for i in range(len(recoveries)):
        sent = recoveries[i][0]
        for j in range(len(recoveries)):
            if i != j:
                received = recoveries[j][1]
                if sent[i] < received[i]:
                    print("ORPHAH MESSAGES DETECTED!")

                
print(f"No orphan messages present. Consistent state!")
            

