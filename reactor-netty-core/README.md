- [reactor-netty](#reactor-netty)
  - [Java NIO](#java-nio)
  - [Netty](#netty)
  - [Inbound 전체적인 과정](#inbound-전체적인-과정)
  - [Source Code로 더 자세히 살펴보기](#source-code로-더-자세히-살펴보기)
  - [Flux](#flux)
  - [Backpressure](#backpressure)
- [결론](#결론)

# reactor-netty

R2DBC driver에 대해서 알아보면서 R2DBC MySQL driver와 PostgreSQL driver가 Non-blocking으로 작동하기 위해서 `reactor-netty`를 사용하는 것을 알았다. 🤔 **`reactor-netty`는 `reactive stream specification`을 따르는 `reactor project`와 어떻게 연결되어 있을까?**

`reactor-netty`로 간단하게 `TcpClient`를 만들면 아래와 같다. server에 접속하여 `hello world!`를 보내고, server로부터 response를 수신하는 간단한 코드이다. `reactive stream specification`를 따라서 `Publisher` - `Subscriber` 관계를 가지고 `subscribe()`를 해야지 data flow가 시작이 된다. 

```java
public class Example {
    public static void main(String[] args){

        Connection connection = TcpClient.create()
                .host("localhost")
                .port(8080)
                .connectNow();

        connection.outbound()
                .sendString(Mono.just("hello world!"))
                .then()
                .subscribe();

        connection.inbound()
                .receive()
                .asString()
                .subscribe();

        connection.onDispose().block();
    }
}
```

그리고 `backpressure`를 지원하게 된다. Subscriber는 자기 처리 속도에 맞춰서 Publisher에게 데이터를 요청 할 수 있다. 간단하게 아래처럼 `ExampleSubscriber` class를 만들었다. subscribe가 되었을 때 `onSubscribe`를 통해서 `Publisher`에 데이터를 요청(`request(take)`)를 한다. `onNext`를 통해서 `Iterator`처럼 데이터를 처리하는데, 여기서는 간단하게 추가로 `request(1)`를 하도록 하였다.

```java
public class ExampleSubscriber implements Subscriber<String> {
    final int take;
    Subscription subscription;

    public ExampleSubscriber(int take) {
        this.take = take;
    }

    @Override
    public void onSubscribe(Subscription s) {
        subscription = s;
        subscription.request(take);
    }

    @Override
    public void onNext(String s) {
        System.out.println(s);
        subscription.request(1);
    }

    @Override
    public void onError(Throwable t) {

    }

    @Override
    public void onComplete() {
        subscription = null;
    }
}
```

```java
Subscriber<String> exampleSubscriber = new ExampleSubscriber(5);

connection.inbound()
        .receive()
        .asString()
        .subscribe(exampleSubscriber);
```

우리는 아랫처럼 `Double Colon(::) Operator`로 추가할 수 있는데, 이것은 `Lambda function`처럼 작동하게 된다. 이렇게 `Lambda function`으로 설정이 되면 내부적으로 `LambdaSubscriber`가 사용된다. 그리고 우리가 명시적으로 `request(n)`을 하지 않지만, 자동으로 `request(Long.MAX_VALUE)`로 요청하여 `push` 모드로 작동하게 된다.

```java
connection.inbound()
        .receive()
        .asString()
        .subscribe(System.out::println);
```

## Java NIO

Java NIO를 통해서 **Non-Blocking I/O**를 이용할 수 있다. NIO는 `Channel`과 `Buffer`가 있는데, `SocketChannel`이라고 하면 Channel는 network를 통해서 data를 송수신는 역할을 한다. `Buffer`는 이제 read하거나 write할 데이터를 보관하는 메모리 공간이다. 이제 read 과정에서는 `Channel`에서 `Buffer`로 데이터가 저장되고, write 과정에서는 `Buffer`에서 `Channel`로 전달되게 된다.

간단하게 `SocketChannel`를 사용하여 서버에 `hello world!` 메세지를 보내고, 응답을 받도록 아랫처럼 작성할 수 있다. `socketChannel.read(byteBuffer)`는 `Channel`에서 `Buffer`로 byte가 쓰여지고, 이제 읽어진 데이터의 byte 수를 리턴한다. data를 읽기 위해서 `byteBuffer.flip()`를 통해서 write mode에서 read mode로 전환한다. 마지막으로 `byetBuffer.compact()`로 이제 `Buffer`를 비우게 되는데, 안 읽은 데이터는 유지하여 맨 앞으로 이동시키는 작업을 한다. 여러 개의 Channel을 동시에 관리하기 위해서 NIO에서 제공하는 `select`를 사용할 수도 있다.

```java
public class Example {
    public static void main(String[] args) throws IOException {
        InetSocketAddress address = new InetSocketAddress("localhost", 8080);
        SocketChannel socketChannel = SocketChannel.open(address);

        Charset charset = StandardCharsets.UTF_8;
        socketChannel.write(charset.encode(CharBuffer.wrap("hello world!")));

        ByteBuffer byteBuffer = ByteBuffer.allocate(81920);
        CharsetDecoder charsetDecoder = charset.newDecoder();
        CharBuffer charBuffer = CharBuffer.allocate(81920);

        while (socketChannel.read(byteBuffer) != -1 || byteBuffer.position() > 0) {
            byteBuffer.flip();
            charsetDecoder.decode(byteBuffer, charBuffer, true);
            charBuffer.flip();
            System.out.println(charBuffer);
            charBuffer.clear();
            byteBuffer.compact();
        }

        socketChannel.close();
    }
}
```

## Netty

Netty는 위에서 설명한 NIO 바탕으로 작동하게 된다. Netty에서는 비슷하게 `Channel`과 `ByteBuf`라는 것이 존재한다. `ByteBuf`는 `ByteBuffer`보다 쉽게 사용할 수 있도록 구현이 되어 있다. `Buffer`를 allocate하고 release하는 작업을 반복하는 것은 비효율적이기 때문에 `ByteBuffer`를 pool로 공유해서 사용할 수 있다. 그런 경우에는 `Reference count`를 통해서 사용하고 있는 Object들을 알 수 있고, reference count가 0일 때 안전하게 release할 수 있도록 할 수 있다.

Netty는 Event Loop위에서 Event가 발생하였을 때 해당 handler를 처리하는 구조를 가지고 있다. multi-threading의 이점을 가져가지 위하여 Event loop을 thread별로 여러 개가 구동될 수 있고, 이러한 event loop들이 event loop group으로 관리된다. `Channel` 하나는 하나의 `Event Loop`, 즉 하나의 thread에 연결된다. **따라서 `Channel`는 `thread safe`한 특성을 가진다.** 

그리고 `ChannelHandler`가 존재하고 이러한 handler로 데이터를 처리하는 로직을 구성할 수 있다. 이러한 handler는 chain처럼 여러 개로 연결되어 작동할 수 있다.

## Inbound 전체적인 과정

1. Transport Layer에서 TCP 통신을 통해서 client의 요청이 전달되고, server로부터 응답을 받게 된다.
2. 서버에서 보낸 값이 Kernel space에서 socket receive buffer에 쌓인다. (ack을 보내고 유실된 패킷을 다시 받는다. congestion window, receive window 등을 통해서 flow control을 하게 된다)
3. Channel은 Event Loop에 등록이 되어 있어서 read를 통해서 kernel space의 데이터를 user space의 ByteBuf로 가져온다. 기본적으로 Auto Read가 true기 때문에 우리가 명시적으로 Channel read를 하지 않아도 Netty Event Loop에서 읽게 된다. 그리고 이렇게 read가 될 때, onInboundNext를 call이 되어 해당 데이터가 전달된다.


## Source Code로 더 자세히 살펴보기

Inbound 과정에서 Publisher 역할을 하는 `FluxReceive`와 다양한 Operator를 제공하는 `ByteBufFlux`를 이해해본다.

`ByteBufFlux`는 `FluxOperator`를 상속받고 있다.
```java
public class ByteBufFlux extends FluxOperator<ByteBuf, ByteBuf>
```

`FluxReceive`는 `Flux`를 상속받고 `Subscription` interface를 구현하고 있다.
```java
final class FluxReceive extends Flux<Object> implements Subscription, Disposable
```

아랫의 주석처럼 `receive()`는 `ByteBufFlux`를 반환하는데, ByteBufFlux가 `source`로 `FluxReceive` 객체를 가지게 된다. `asString()`은 `ByteBufFlux`가 제공하는 operator로 이제 `ByteBuf`에서 메세지를 읽어서 String으로 전달해주는 역할을 한다. 따라서 `asString()` 이후에는 `Flux<String>`이 전달된다.

`subscribe()`를 하면 `ByteBufFlux`의 subscribe가 실행되고 이는 다시 `FluxReceive`의 subscribe를 실행하게 된다.

```java
connection.inbound() // NettyInbound
        .receive() // ByteBufFlux
        .asString() // Flux<String>
        .subscribe();
```

`FluxReceive`의 `subscribe()`이 Call되면 `s.onSubscribe(this)`가 실행된다. 

FluxReceive.java
```java
@Override
public void subscribe(CoreSubscriber<? super Object> s) {
    // 생략
    startReceiver(s);
    // 생략
}
```

```java
final void startReceiver(CoreSubscriber<? super Object> s) {
    if (once == 0 && ONCE.compareAndSet(this, 0, 1)) {
        // 생략
        receiver = s;

        s.onSubscribe(this);
    }
    // 생략
}
```

`.subscribe(exampleSubscribe)`로 설정하면 `onSubscribe`에서 `request(5)`를 하게 되고, `.subscribe(System.out::println)`처럼 Lambda expression으로 설정을 하면 `LambdaSubscriber`로 `request(long.MAX_VALUE)`가 된다. `receiverDemand`는 최대값이 `long.MAX_VALUE`가 될 수 있도록 `Operators.addCap(long a, long b)`를 사용해서 증가시킨다.

FluxReceive.java
```java
@Override
public void request(long n) {
    // 생략
    this.receiverDemand = Operators.addCap(receiverDemand, n);
    drainReceiver();
    //생략
}
```

그리고 `drainReceiver` 메소드가 실행이 된다. `FluxReceive`의 Constructor를 보면 `channel.config().setAutoRead(false)`로 Netty가 자동으로 Channel을 read하지 않도록 한다. 그리고 read가 필요할 때에 `channel.config().setAutoRead(true)`로 설정하여 Netty에서 자동으로 Channel read를 하도록 한다. 이렇게 read 컨트롤을 auto read flag로 하게 된다.

```java
FluxReceive(ChannelOperations<?, ?> parent) {
    // 필요한 부분만 남기고 생략

    channel.config()
            .setAutoRead(false);
}
```

 auto read로 Netty가 자동으로 Channel read를 하면 `onInboundNext` 함수가 call된다. 내부에서 `FluxReceive`의 `onIboundNext`를 다시 call하는 것을 확인할 수 있다. 

ChannelOperations.java
```java
protected void onInboundNext(ChannelHandlerContext ctx, Object msg) {
    inbound.onInboundNext(msg); // inbound는 FluxReceive
}
```

`FluxReceive`의 `drainReceiver` 메소드를 보면 Subscriber가 요청한 request(n)의 n개 만큼 Queue에서 가져와서 `a.onNext(v)`로 downstream으로 내려준다. 하지만 `LambdaSubscriber`처럼 `request(long.MAX_VALUE)`가 된다면 Queue에 넣어서 작동하지 않고 바로 `onNext()`로 내려주도록 `receiverFastpath`를 true로 설정한다.

FluxReceive.java
```java
final void drainReceiver() {
    // 필요한 부분만 남기고 생략
    for (;;) {
        final Queue<Object> q = receiverQueue;
        final CoreSubscriber<? super Object> a = receiver;

        long r = receiverDemand;
			long e = 0L;

			while (e != r) {
                Object v = q != null ? q.poll() : null;
				boolean empty = v == null;

                if (empty) {
					break;
				}

                try {
                    a.onNext(v);
                }
                finally {
                    ReferenceCountUtil.release(v);
                }

                e++;
            }

            if (r == Long.MAX_VALUE) {
				receiverFastpath = true;
				if (needRead) {
					needRead = false;
					channel.config()
					       .setAutoRead(true);
				}
            }

            if ((receiverDemand -= e) > 0L || (e > 0L && q.size() < QUEUE_LOW_LIMIT)) {
				if (needRead) {
					needRead = false;
					channel.config()
					       .setAutoRead(true);
				}
			}
    }
}
```

이제 `channel.config().setAutoRead(true)`로 auto read가 설정되면 Netty에서 Channel read가 자동으로 이루어지고 `onInboundNext`가 call이 된다. 이제 이렇게 channel read가 자동으로 이루어지면 바로 처리하지 않고 다시 Queue에다가 넣고, 나중에 `drainReceiver` call에서 다시 처리하게 된다. 그리고 아래 코드를 보면 이제 `request(long.MAX_VALUE)`이면 Queue에다가 다시 넣어서 `drainReceiver()`로 처리하지 않고 바로 `onNext`로 전달하게 된다.

```java
final void onInboundNext(Object msg) {
    // 필요한 부분만 남기고 생략
    if (receiverFastpath && receiver != null) {
        receiver.onNext(msg);
    }
    else {
        Queue<Object> q = receiverQueue;
        q.offer(msg);
        drainReceiver();
    }
```

Netty의 Channel read로부터 어떻게 data가 reactive stream으로 전달되는지 확인했다. `ByteBufFlux`는 이제 `FluxOperator`를 상속받아서 다양한 `operator`를 제공한다고 설명하였다. 그중의 하나인 `asString()`을 통해서 `Flux<String>`을 return한다.

```java
connection.inbound() // NettyInbound
        .receive() // ByteBufFlux
        .asString() // Flux<String>
        .subscribe();
```

`asString()`의 아래의 코드를 살펴보면 `Flux`의 `handle` 메소드를 사용하고 있는 것을 확인할 수 있다. `ByteBuf`가 전달되고 있기 때문에 `bb`는 `ByteBuf`가 된다. 이제 ByteBuf로부터 읽은 데이터를 String으로 변환하여 `sink.next()`로 downstream으로 전달하게 된다.

ByteBufFlux.java
```java
public final Flux<String> asString() {
    return asString(Charset.defaultCharset());
}

public final Flux<String> asString(Charset charset) {
    Objects.requireNonNull(charset, "charset");
    return handle((bb, sink) -> {
        try {
            sink.next(bb.readCharSequence(bb.readableBytes(), charset).toString());
        }
        catch (IllegalReferenceCountException e) {
            sink.complete();
        }
    });
}
```

## Flux

Flux는 `generate`와 `create`라는 메소드도 제공한다. `generate`를 통해서 Flux 생성을 하면 아래와 같다. 첫번째 인자는 초기 state를 설정하는 lambda function이고, state를 0으로 설정하였다. 그리고 두번째 인자는 BiFunction으로 state값과 SynchronousSink를 제공한다.

```java
Flux<String> flux = Flux.generate(
        () -> 0,
        (state, sink) -> {
            sink.next("sink: " + state);
            if (state == 20) sink.complete();
            return state + 1;
        }
);

flux.subscribe(System.out::println);
```

`create`로는 아래와 같이 Flux를 생성할 수 있다. 이제 create의 emitter(sink)로는 여러개의 element를 emit할 수 있고, 비동기적으로 작동하게 할 수도 있다. `sink` 객체를 저장하고 다른 thread에서 sink.next()를 비동기적으로 실행하도록 할수도 있다.

```java
Flux<Integer> fluxAsync = Flux.create(
        sink -> {
            sink.next(1);
            sink.complete();
        }
);

fluxAsync.subscribe(System.out::println);
```

## Backpressure

Backpressure는 Consumer가 처리할 수 있는 만큼 Producer에 요청할 수 있는 메카니즘을 제공한다. 따라서 Consumer의 처리가 늦더라도 Producer가 계속해서 데이터를 downstream으로 전달하지 않는다.

위에서 `reactor-netty`로 TCP 통신을 하게 되면 두 리소소 사이의 backpressure은 TCP transport layer의 flow control 메카니즘(congestion window, receive window)에 의해서 작동하게 된다. 어플리케이션 단의 컨텍스트를 이해하여 request하고 해당 request한 아이템 만큼 downstream으로 push하지 못한다.

Server가 계속해서 data를 client에게 보내면 일단은 Client쪽의 receive buffer에 계속 쌓이게 되고, Netty가 자동으로 읽어서 Queue에다가 계속 쌓게 된다. 그리고 Consumer의 request 갯수 만큼 Queue에서 가져와서 처리하게 된다.

Netty가 Channel read하는 속도가 느려서 receive buffer가 꽉차게 되면 이제 receive window는 0이 되고 server에서 더이상 packet을 보낼 수 없게 된다.

그리고 어플리케이션 입장에서는 처리되지 못한 data들이 release되지 못하고 Queue에 계속해서 쌓여나가게 된다. receive window가 0으로 server가 더 이상 패킷을 보내기전에 어플리케이션에서 Out of memory가 발생할 가능성도 존재한다.

RSocket은 network boundary를 넘어서 reactive stream의 push-pull model을 제공하는 binary protocol을 제공한다. 따라서 하나의 connection을 multiplexer처럼 공유해서 사용하는데도 유리하다.

# 결론

`reactor-netty`는 `reactive stream specification`을 따르는 `reactor project`를 적용하여 쉽게 server와 client를 작성할 수 있다. 

`reactor-netty`에서 어떻게 `reactive stream`을 생성하여 downstream으로 데이터를 전달하는지 코드를 통해서 살펴보았다. 이를 통해서 `reactor-netty`는 TCP flow control 메카니즘에 backpressure를 의존하는 것을 확인할 수 있었다. socket receive buffer가 가득차서 receive window가 0이 된다면 더이상 network상에서 패킷을 보낼 수가 없게 된다. 그러면 데이터를 보내는 쪽에서는 socket send buffer가 가득 찰 수 있다. 보내는 쪽에서는 동기적인 코드에서는 계속해서 block이 될 수 있고, 비동기적인 코드에서는 계속해서 실행이 미뤄질 수 있다. 

그리고 Consumer가 request하는 것과 상관없이 Netty가 Channel에서 읽은 데이터를 Consumer에서 처리하기 전까지 Queue에 쌓아 놓는 것을 알 수 있었다. 따라서 경우에 따라서는 application 단에서 Queue에 계속 쌓이는 데이터에 의해서 out of memory가 발생할 수 있는 가능성이 존재한다는 것을 이해할 수 있었다.

RSocket은 Network boundary를 넘어서 reactive stream의 push-pull 메카니즘을 적용할수 있는 binary protocol이라는 것을 알게 되었고, 나중에 Rsocket에 대해서도 자세히 알아보면 좋겠다.

하지만 R2DBC driver 같은 경우에는 Database에서 RSocket을 사용할 수 있는 Protocol을 제공하지 않기 때문에 옵션이 될 수 없겠다.