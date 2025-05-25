package com.vineet.raft.core.log;

import com.vineet.raft.api.dto.ClientCommand;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class Command {

    private CommandInstruction instruction;
    private String key;
    private String value;

    public ClientCommand toClientCommand() {
        ClientCommand cc = new ClientCommand();
        cc.setKey(key);
        cc.setValue(value);
        cc.setInstruction(instruction);
        return cc;
    }

    public com.vineet.raft.grpc.Command toGrpcCommand() {
        return com.vineet.raft.grpc.Command.newBuilder().setKey(key).setValue(value).setInstruction(instruction.toGrpcCommandInstruction()).build();
    }

    public static Command fromGrpcCommand(com.vineet.raft.grpc.Command cc) {
        Command c = new Command();
        c.setKey(cc.getKey());
        c.setValue(cc.getValue());
        c.setInstruction(CommandInstruction.fromGrpcCommandInstruction(cc.getInstruction()));
        return c;
    }

}
