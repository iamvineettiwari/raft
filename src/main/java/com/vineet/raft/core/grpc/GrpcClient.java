package com.vineet.raft.core.grpc;

import com.vineet.raft.core.node.role.CurrentRole;
import com.vineet.raft.grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class GrpcClient {
    private final Logger logger = LoggerFactory.getLogger(GrpcClient.class);

    public void sendLogToFollower(CurrentRole role, String followerAddress, LogRequest request) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(followerAddress).usePlaintext().build();
        RaftServiceGrpc.RaftServiceStub stub = RaftServiceGrpc.newStub(channel);

        logger.info("Sending log to {} with request {}", followerAddress, request.toString());

        stub.logRequestCall(request, new StreamObserver<LogResponse>() {
            @Override
            public void onNext(LogResponse logResponse) {
                logger.info("Log request succeeded {}", logResponse.toString());
                role.processLogResponse(
                        logResponse.getFollower(),
                        logResponse.getFollowerTerm(),
                        logResponse.getAckLength(),
                        logResponse.getSuccess()
                );
                channel.shutdown();
            }

            @Override
            public void onError(Throwable throwable) {
                logger.error(
                        "Log request failed for {} - {}",
                        followerAddress,
                        throwable.getMessage(),
                        throwable
                );
                channel.shutdown();
            }

            @Override
            public void onCompleted() {
                logger.info("Sending log to {} completed", followerAddress);
                channel.shutdown();
            }
        });
    }

    public void sendVoteRequestToFollower(CurrentRole role, String followerAddress, VoteRequest request) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(followerAddress).usePlaintext().build();
        RaftServiceGrpc.RaftServiceStub stub = RaftServiceGrpc.newStub(channel);

        logger.info("Sending vote request to {} with request {}", followerAddress, request.toString());

        stub.voteRequestCall(request, new StreamObserver<VoteResponse>() {
            @Override
            public void onNext(VoteResponse voteResponse) {
                logger.info("Vote request succeeded {}", voteResponse.toString());

                role.processVoteResponse(voteResponse.getVoter(), voteResponse.getVoterTerm(), voteResponse.getSuccess());
                channel.shutdown();
            }

            @Override
            public void onError(Throwable throwable) {
                logger.error(
                        "Vote request failed for {} - {}",
                        followerAddress,
                        throwable.getMessage(),
                        throwable
                );
                channel.shutdown();
            }

            @Override
            public void onCompleted() {
                logger.info("Sending vote request to {} completed", followerAddress);
                channel.shutdown();
            }
        });
    }
}
