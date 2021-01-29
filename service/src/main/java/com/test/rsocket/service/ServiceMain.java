package com.test.rsocket.service;

import java.util.Optional;

import com.test.rsocket.rpc.SimpleServiceServer;

import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.server.TcpServerTransport;
import reactor.core.publisher.Mono;

public class ServiceMain {
    public static void main(String[] args) {
        SimpleServiceServer serviceServer = new SimpleServiceServer(new DefaultSimpleService(), Optional.empty(), Optional.empty(), Optional.empty());

        RSocketServer.create((setup, sendingSocket) -> Mono.just(serviceServer))
                .bindNow(TcpServerTransport.create(8081))
                .onClose()
                .block();
    }
}
