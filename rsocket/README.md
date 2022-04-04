# RSocket

R2DBC MySQL Driver, R2DBC PostgreSQL Driver, Webflux WebClient 모두 `reactor-netty`를 사용하여 구현하고 있다. `Database Client / Database Server`나 `Weblcient / Server`의 통신에서 실제로 client가 요청하는 request n 만큼 서버가 주는 것이 아니라 TCP layer에서는 계속 패킷이 전달되고 `Client / reactor-netty`와 `reactor-netty / Server` 간에 pull-push가 작동된다. (Cusor를 사용하여 fetch size를 request n에 따라서 dynamic하게 전달한다면 가능하다. 하지만 r2dbc mysql driver와 postgresql driver에서는 단순하게 Cursor를 사용하여 fetch size 만큼 데이터베이스에서 가져오도록 설정할 수 있지만, request n에 따라서 Dynamic하게 설정하지는 않고 있다. 하지만 MongoDB reactive stream driver에서는 request에 따라서 fetch size를 Dynamic하게 설정한다.<sup>[1][1]</sup>) Application 컨텍스트에서 consumer가 request n으로 얼마나 요청하는지 전달이 될려면 그에 대한 정보를 Producer에게 전달해야 되는데, 그러한 메카니즘이 없으니 이렇게 작동하는 것이 당연하다. **Rsocket은 이제 Layer 5/6 Communication protocol이고 이러한 정보를 담아서 통신을 하기때문에 Application-level flow control을 가능케한다**

"Rsocket - Future Reactive Protocol by Oleh Dokula"<sup>[2][2]</sup> 발표에서 Rsocket의 특징들을 잘 설명하고 있다. Transport Agnostic으로 유연하게 다양한 Transport를 사용할 수 있다. 따라서 TCP, Websocket 뿐만 아니라 Aeron, QUIC처럼 다른 것들도 사용할 수 있다. 하지만 Rsocket의 Java 구현체인 Rsocket-java는 TCP, Websocket 방식만 지원하고 있다. java말고도 다양한 언어에서 Rsocket을 사용할 수 있는 Driver가 개발되고 있다.<sup>[3][3]</sup>

Rsocket은 `client-server`와 `server-server`의 통신에서 HTTP 1.1과 비교해서는 당연히 Binary Messaging과 Multiplexing이라는 특징때문에 Performance적으로 유리할 것이다. HTTP2는 이미 binary framing과 Multiplexing이 되는데, 그렇게 때문에 HTTP2 위에서 Rsocket이 사용되면 Transport에서 제공하는 것을 활용하여 작동할 수 있다고 설명한다. QUIC도 마찬가지이다. 하지만 Rsocket의 Java 구현체에서는 TCP와 Websocket Transport만 지원한다. <sup>[4][4]</sup>

Rsocket-java에서도 reactor-netty를 사용한다. TCP를 사용할 때는 reactor-netty의 `TcpClient`를 사용하여 통신을 하게 된다. 따라서 inbound 과정에서는 socket receive buffer에 쌓이고 이것을 Channel로 읽어서 ByteBuf에 가져오고, 이것을 Queue에 쌓아서 Consumer가 요청한 request만큼 가져와서 처리한다. (request가 `Long.MAX_VALUE`로 설정되어 `push-only`로 작동하게 되면 Queue에 담아 놓지 않고 바로 Consumer에게 push해준다.) 이전에 `reactor-netty`가 어떻게 작동하는지 소스코드로 확인을 했었다 <sup>[5][5]</sup> 이러한 메카니즘은 동일할 것이고, 단지 Rsocket Protocol에서 얼마나 요청하는지 정보가 담겨 있어서 Network boundary를 넘어서 수신하는 쪽이 요청하는 양에 따라서 응답을 해줄 수 있을 것이다. 

RSocket Protocol에서 `REQUEST_N Frame`<sup>[6][6]</sup>이 존재한다. 이렇게 전달된 frame의 데이터를 가지고 request하는 것을 코드로 확인할 수 있다.

`RSocketResponder.java`
```java
case REQUEST_N:
    receiver = super.get(streamId);
    if (receiver != null) {
    long n = RequestNFrameCodec.requestN(frame);
    receiver.handleRequestN(n);
    }
    break;
```

`RequestStreamResponderSubscriber.java`
```java
final class RequestStreamResponderSubscriber
    implements ResponderFrameHandler, CoreSubscriber<Payload> {
    // 설명에 필요한 부분 빼고 생략

    @Override
    public void handleRequestN(long n) {
        this.s.request(n);
    }
}
```

RSocket은 request/response 뿐만 아니라, `Fire&Forget`, `Request Stream`, 양방향으로 stream 연결을 하는 `Request Channel`을 사용할 수 있다. Rosocket-java 깃헙 리포에서 제공하는 예제<sup>[7][7]</sup>를 참고하여 `Request Stream`의 경우를 아래처럼 작성할 수 있다.

```java
RSocketServer.create(
        SocketAcceptor.forRequestStream(
                payload ->
                    Flux.interval(Duration.ofMillis(1000))
                            .map(aLong -> {
                                System.out.println("Server: " + aLong);
                                return DefaultPayload.create(aLong.toString());
                            })

        )
).bindNow(TcpServerTransport.create("localhost", 7000));

RSocket socket = RSocketConnector.create()
        .setupPayload(DefaultPayload.create("test", "test"))
        .connect(TcpClientTransport.create("localhost", 7000))
        .block();

socket.requestStream(DefaultPayload.create("hello"))
        .subscribe(new Subscriber<Payload>() {
            private Subscription subscription;
            @Override
            public void onSubscribe(Subscription s) {
                this.subscription = s;
                subscription.request(1);
            }

            @Override
            public void onNext(Payload payload) {
                String data = payload.getDataUtf8();
                System.out.println("Client: " + data);
                if (Integer.parseInt(data) == 10) {
                    subscription.cancel();
                }
                subscription.request(2);
            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onComplete() {

            }
        });
```

# 결론

🤔 어떤 경우에 RSocket을 효과적으로 사용할 수 있을까?

- Reactive Stream으로 Application Level에서도 Flow Control이 필요할 경우. (그런데 많은 웹서버에서 데이터를 데이터베이스에서 가져와서 전달하게 된다. 그러면 결국 데이터베이스와 통신에 의존하게 된다. IoT 장비처럼 자체적으로 데이터를 생성하는 경우에 이러한 Application Level Flow Control이 더 의미가 있는 것일까?)
- gRPC처럼 다양한 구현체가 존재하여 다양한 언어에서 제공하는 것처럼 RSocket도 다양한 언어의 구현체가 존재한다. Polyglot 마이크로 서비스 환경에서 사용할 수 있을 것이다.
- 단순한 Synchronous한 HTTP request/response말고 Fire&Forget, RequestStream, RequestChannel등 다양한 방식을 사용해야 되는 경우 RSocket을 활용할 수 있을 것이다.
- 연결이 끊어지거나 할 때 다시 접속하는 메카니즘이나 client load balancing도 제공하기 때문에 이러한 것들이 필요한 경우에 활용할 수 있다.

🤔 하지만 Production에 활용하기에 이러한 것들이 걱정된다.

- 제일 먼저 이 프로젝트가 활발히 진행되고 있는지 걱징이 된다. 그래도 Rsocket java 구현체 코드는 계속 업데이트가 되고 있다.
- Rsocket을 실제로 적용한 사례들에 대한 Reference가 구글링해도 별로 나오지 않는다.

그리고 추가적으로 이러한 생각들이 든다.

- RSocket Protocol Specification은 Zipkin에 대한 언급이 있다. 그리고 누군가 Prometheus로 수집할 수 있는 패키지를 오픈소스로 올린 것이 보인다. 하지만 실제로 모니터링들이 어떻게 되는지 테스트 해봐야겠다.
- Documentation은 어떻게 해야 할까?
- Envoy같은 것을 사용해서 Service mesh를 사용하고 있을 때도 RSocket을 사용할 수 있을까?

아직 RSocket에 대해서 해결되지 못한 궁금증이 많고, Production에서 사용될 수 있을지 의문이다. 하지만 Spring WebFlux등으로 reactive programming을 하게 된다면, 앞으로 관심을 가지고 지켜볼 만한 Project라고 생각된다.

[1]: https://github.com/jayground8/spring/tree/main/mongodb-reactive-stream-driver

[2]: https://www.youtube.com/watch?v=KapSjhUYSz4&t=668s

[3]: https://github.com/rsocket

[4]: https://github.com/rsocket/rsocket-java/tree/master/rsocket-transport-netty/src/main/java/io/rsocket/transport/netty

[5]: https://github.com/jayground8/spring/tree/main/reactor-netty-core

[6]: https://rsocket.io/about/protocol#request_n-frame-0x08

[7]: https://github.com/rsocket/rsocket-java/blob/c80b3cb6437046f3ccc79136a66299448f58c561/rsocket-examples/src/main/java/io/rsocket/examples/transport/tcp/stream/ClientStreamingToServer.java