### Introduction

Raft is a consensus algorithm designed to ensure that all nodes in a distributed system agree on a consistent and reliable sequence of operations. This guarantees a total order of committed entries, which is essential for maintaining correctness and fault tolerance in distributed environments.

In this project, I have implemented the Raft algorithm using a combination of tools and frameworks, including Spring Boot for building the service layer and gRPC for efficient inter-node communication.

### Node States in a Nutshell

- Every node starts in the **FOLLOWER** state.  
- If a **FOLLOWER** suspects a **LEADER** failure, it transitions to the **CANDIDATE** state.  
- If a **CANDIDATE** receives votes from a quorum, it transitions to the **LEADER** state.  
- If a **CANDIDATE** fails to receive votes from a quorum or discovers a node with a higher term number, it transitions back to the **FOLLOWER** state.  
- If the election times out or results in a tie, a new election is started with an incremented term number.  
- If a **LEADER** discovers another **LEADER** with a higher term number, it transitions to the **FOLLOWER** state.

### Three Server configuration steps

```shell
mvn clean install
```

```shell
java -jar target/Raft-0.0.1-SNAPSHOT.jar --server.port=8000 --id=node1 --peers=localhost:8001,localhost:8002 --storage=./node1/
java -jar target/Raft-0.0.1-SNAPSHOT.jar --server.port=8001 --id=node2 --peers=localhost:8000,localhost:8002 --storage=./node2/
java -jar target/Raft-0.0.1-SNAPSHOT.jar --server.port=8002 --id=node3 --peers=localhost:8000,localhost:8001 --storage=./node3/
```

### Default Configurations

- Election timer -> Random (150 - 300) ms, to change timer value, pass `--eTime=<REQ_NUM>`
- Heartbeat timer -> Random (50 - 150) ms, to change timer value, pass `--hTime=<REQ_NUM>`

### Note:
Ensure that the election timeout is set **greater than the heartbeat interval**, and keep the **number of nodes in the system odd** to avoid repeated leader elections and system instability.

### APIs

1. Get the state of the node including current term and current leader
  ```
  curl --location 'http://localhost:<node_port>'
  ```
2. Submit Command to system
  ```
  curl --location 'http://localhost:<any_node_port>/command' \
  --header 'Content-Type: application/json' \
  --data '{
    "instruction": "SET" | "DELETE",
    "key": "message",
    "value": "Hello World"
  }'
  ```
