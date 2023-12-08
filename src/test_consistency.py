import sys
import ast
import numpy as np

num_of_nodes = int(sys.argv[1])

checkpoint_sequences = [0, 1, 3]



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

result = "successful"


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

for k in range(len(checkpoint_sequences)):

    checkpoint_set = (np_states[:, k]).tolist()
    #print(checkpoint_set)

    # check each pair is concurrent
    for i in range(len(checkpoint_set)):
        for j in range(i+1, len(checkpoint_set)):

            if not(is_concurrent(checkpoint_set[i], checkpoint_set[j])):
                result = "inconsistent"
                break
        
        if result == "inconsistent":
            break
print(result)

