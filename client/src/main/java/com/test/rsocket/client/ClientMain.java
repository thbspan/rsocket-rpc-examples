package com.test.rsocket.client;

import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.CyclicBarrier;

import com.google.protobuf.Empty;
import com.test.rsocket.rpc.SimpleRequest;
import com.test.rsocket.rpc.SimpleResponse;
import com.test.rsocket.rpc.SimpleServiceClient;

import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.transport.netty.client.TcpClientTransport;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class ClientMain {

    public static void main(String[] args) throws InterruptedException, BrokenBarrierException {
        RSocket rSocket = RSocketConnector.create()
                .connect(TcpClientTransport.create(8081))
                .block();

        if (rSocket != null) {
            try {
                SimpleServiceClient serviceClient = new SimpleServiceClient(rSocket);
                Flux<SimpleRequest> requests =
                        Flux.range(1, 11)
                                .map(i -> "sending -> " + i)
                                .map(s -> SimpleRequest.newBuilder().setRequestMessage(s).build());

                CyclicBarrier cyclicBarrier = new CyclicBarrier(2);
                Runnable completeConsumer = () -> {
                    try {
                        cyclicBarrier.await();
                    } catch (InterruptedException | BrokenBarrierException e) {
                        e.printStackTrace();
                    }
                };
                serviceClient.streamingRequestSingleResponse(requests)
                        .subscribe(response -> System.out.println(response.getResponseMessage()), System.err::println, completeConsumer);
                cyclicBarrier.await();

                Mono<SimpleResponse> responseMono = serviceClient.requestReply(SimpleRequest.newBuilder().setRequestMessage("test").build());
                SimpleResponse simpleResponse = responseMono.block();
                if (simpleResponse != null) {
                    System.out.println(simpleResponse.getResponseMessage());
                }

                Mono<Empty> emptyMono = serviceClient.fireAndForget(SimpleRequest.newBuilder().setRequestMessage("fireAndForget").build());
                Empty empty = emptyMono.block();
                System.out.println(empty);

                Flux<SimpleResponse> responseFlux = serviceClient.requestStream(SimpleRequest.newBuilder().setRequestMessage("requestStream").build());

                responseFlux.limitRate(3).onBackpressureBuffer()
                        .subscribe(response -> System.out.println(response.getResponseMessage()), System.err::println, completeConsumer);
                cyclicBarrier.await();
            } finally {
                rSocket.dispose();
            }
        }

    }
}
