$$ NodeStates $$

1.  Every node starts as a `FOLLOWER` state
2.  If `FOLLOWER` suspects `LEADER` failure, it transists to `CANDIDATE` state
3.  If `CANDIDATE` receives votes from quorum, it transists to `LEADER` state
4.  If `CANDIDATE` fails to receive votes from quorum or discovers any node with higher term number, it transists to `FOLLOWER` state
5.  If election times out / votes are equal, a new election is started with higher term number
6.  If a `LEADER` discovers any new `LEADER` with higher term number, it transists to `FOLLOWER` state

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

### Note: Keep the election timer greater than the heartbeat timer to avoid repeated Leader elctions and instability
