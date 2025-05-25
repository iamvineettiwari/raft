package com.vineet.raft.api.controller;

import com.vineet.raft.api.dto.ClientCommand;
import com.vineet.raft.api.dto.NodeStatus;
import com.vineet.raft.core.log.Command;
import com.vineet.raft.core.node.Node;
import com.vineet.raft.exceptions.InvalidRequestException;
import com.vineet.raft.exceptions.NoLeaderFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("")
public class RaftController {
    private final Logger logger = LoggerFactory.getLogger(RaftController.class);

    private final Node node;

    @Autowired
    public RaftController(Node node) {
        this.node = node;
    }

    @GetMapping("/")
    public ResponseEntity<NodeStatus> getStatus() {
        return ResponseEntity.ok(NodeStatus.builder().currentLeader(node.getCurrentLeader()).currentTerm(node.getCurrentTerm()).data(node.getData()).build());
    }

    @PostMapping("/command")
    public ResponseEntity<String> submitCommand(@RequestBody ClientCommand clientCommand) {
        Command command = clientCommand.toNodeCommand();

        try {
            node.submitCommand(command);
            return ResponseEntity.ok("Command accepted");
        } catch (NoLeaderFoundException ignored) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body("No leader found");
        } catch (InvalidRequestException err) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid request - " + err.getMessage());
        } catch (Exception e) {
            this.logger.error("Error submitting command", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Internal server error");
        }
    }


}
