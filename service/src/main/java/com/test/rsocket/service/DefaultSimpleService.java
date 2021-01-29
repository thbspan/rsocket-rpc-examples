package com.test.rsocket.service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Empty;
import com.test.rsocket.rpc.SimpleRequest;
import com.test.rsocket.rpc.SimpleResponse;
import com.test.rsocket.rpc.SimpleService;

import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class DefaultSimpleService implements SimpleService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultSimpleService.class);

    @Override
    public Mono<SimpleResponse> requestReply(SimpleRequest message, ByteBuf metadata) {
        return Mono.fromCallable(() -> SimpleResponse.newBuilder()
                .setResponseMessage("we got the message -> " + message.getRequestMessage())
                .build());
    }

    @Override
    public Mono<Empty> fireAndForget(SimpleRequest message, ByteBuf metadata) {
        LOGGER.info("got message -> {}", message.getRequestMessage());
        return Mono.just(Empty.getDefaultInstance());
    }

    @Override
    public Flux<SimpleResponse> requestStream(SimpleRequest message, ByteBuf metadata) {
        String requestMessage = message.getRequestMessage();
        return Flux.interval(Duration.ofMillis(200))
                // 背压
                .onBackpressureBuffer()
                .map(i -> i + " - got message - " + requestMessage)
                .map(s -> SimpleResponse.newBuilder().setResponseMessage(s).build());
    }

    @Override
    public Mono<SimpleResponse> streamingRequestSingleResponse(Publisher<SimpleRequest> messages, ByteBuf metadata) {
        return Flux.from(messages)
                .windowTimeout(10, Duration.ofSeconds(500))
                .take(1)
                .flatMap(Function.identity())
                .reduce(new ConcurrentHashMap<Character, AtomicInteger>(), (map, s) -> {
                    char[] chars = s.getRequestMessage().toCharArray();
                    for (char c : chars) {
                        map.computeIfAbsent(c, _c -> new AtomicInteger()).incrementAndGet();
                    }
                    return map;
                })
                .map(map -> {
                    StringBuilder builder = new StringBuilder();
                    map.forEach((character, atomicInteger) -> builder.append("character -> ")
                            .append(character)
                            .append(", count -> ")
                            .append(atomicInteger.get())
                            .append('\n'));
                    return SimpleResponse.newBuilder().setResponseMessage(builder.toString()).build();
                });
    }

    @Override
    public Flux<SimpleResponse> streamingRequestAndResponse(Publisher<SimpleRequest> messages, ByteBuf metadata) {
        return Flux.from(messages).flatMap(e -> requestReply(e, metadata));
    }
}
