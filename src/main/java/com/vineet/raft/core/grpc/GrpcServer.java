package com.vineet.raft.core.grpc;

import com.vineet.raft.core.node.Node;
import com.vineet.raft.grpc.LogRequest;
import com.vineet.raft.grpc.LogResponse;
import com.vineet.raft.grpc.RaftServiceGrpc.RaftServiceImplBase;
import com.vineet.raft.grpc.VoteRequest;
import com.vineet.raft.grpc.VoteResponse;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
public class GrpcServer extends RaftServiceImplBase {
    private final Logger logger = LoggerFactory.getLogger(GrpcServer.class);
    private final Node node;

    @Autowired
    public GrpcServer(Node node) {
        this.node = node;
    }

    @Override
    public void logRequestCall(LogRequest request, StreamObserver<LogResponse> responseObserver) {
        logger.info("Received log request: Request {}, ", request.toString());

        LogResponse response = this.node.processLogRequest(
                request.getLeader(),
                request.getLeaderTerm(),
                request.getPrefixLength(),
                request.getPrefixTerm(),
                request.getCommitLength(),
                request.getSuffixList()
        );

        logger.info("Responding to log request {}", response);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void voteRequestCall(VoteRequest request, StreamObserver<VoteResponse> responseObserver) {
        logger.info("Received vote request: Request {}, ", request.toString());

        VoteResponse response = this.node.processVoteRequest(
                request.getCandidate(),
                request.getCandidateTerm(),
                request.getCandidateLogLength(),
                request.getCandidateLastTerm()
        );

        logger.info("Responding to vote request {}", response);
        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
