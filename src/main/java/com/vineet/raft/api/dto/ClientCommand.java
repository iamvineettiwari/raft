package com.vineet.raft.api.dto;

import com.vineet.raft.core.log.Command;
import com.vineet.raft.core.log.CommandInstruction;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ClientCommand {
    private CommandInstruction instruction;
    private String key;
    private String value;

    public Command toNodeCommand() {
        Command cmd = new Command();
        cmd.setKey(key);
        cmd.setValue(value);
        cmd.setInstruction(instruction);
        return cmd;
    }
}
