# Spring MVC에서 R2DBC 사용

`Spring Web MVC + JDBC database driver`와 `Spring Web MVC + R2DBC driver`의 Performance 비교<sup>[1][1] </sup>에 관한 글을 보았다. 🤔 **blocking이 되는 Spring Web MVC에서 Non-blocking R2DBC driver를 사용하는 것이 어떠한 이득이 될지 궁금해졌다.**

## R2DBC SPI

R2DBC는 Service Provider Interface를 제공하고 있다. Driver를 개발하는 쪽에서 이 SPI에 따라서 작성을 해야된다. 당연히 Driver는 Non-blocking하게 작동해야 한다.

## Driver

Driver는 R2DBC specification에 맞춰서 구현되고, Project Reactor, RxJava와 같은 Reactive Stream API를 포함하게 된다. 🤔 **Driver는 어떻게 비동기적으로 작동할 수 있을까?** 많은 드라이버가 Netty를 이용하여 client를 만들고 있다.

## Reactor-Netty

Netty는 Event Loop을 통해서 Non-Blocking & Async하게 작동하게 된다. Reactor Netty는 이제 Netty위에서 Back-Pressure가 가능한 server를 구성할 수 있다. 🤔 **WebFlux를 Netty와 함께 구성하면 사용할 수 있는 Event loop이 이미 있을텐데, Spring Web MVC에서는 어떻게 되는건지 궁금해졌다.**  

## r2dbc-postgresql

r2dbc-postgresql Github source code<sup>[2][2] </sup>를 살펴보았다. code에서 `ReactorNettyClient` 클래스를 찾을 수 있고, netty의 `TcpClient`를 사용하여 connection을 맺도록 되어 있다. 따라서 `reactor-netty`가 dependency로 있는 것을 확인할 수 있다.

## r2dbc-mysql
r2dbc-mysql Github source code<sup>[3][3] </sup>를 보면 마찬가지로 `ReactorNettyClient` 클래스가 있고, netty의 `TcpClient`를 이용하고 있는 것을 확인 할 수 있다.

## Sample code로 Thread 확인

Spring Web MVC에 r2dbc-postgresql를 사용하여 thread가 어떻게 사용되는지 확인해보았다. Sample project는 `CommandLindRunner`를 통해서 실행하도록 간단하게 만들었다. R2dbcRepository를 통해서 catRepository를 만들고 `Spring Web MVC + JDBC database driver`와 `Spring Web MVC + R2DBC driver`의 Performance 비교 글<sup>[1][1] </sup>처럼 `buffer().blockLast()`로 blocking하도록 작성하였다. 이렇게 blocking한 부분은 다른 Thread에서 실행될 수 있도록 `subscribeOn`을 설정하였다.

```kotlin
override fun run(vararg args: String?) {
    val cats = catRepository
            .findAllByName("Coco")
            .subscribeOn(Schedulers.boundedElastic())
            .buffer().blockLast()
    print(cats)
}
```

IntelliJ의 thread dump로 확인해보면 `reactor-tcp-nio-1`와 `boundedElastic-1`를 확인 할 수 있다. 먼저 `boundedElastic-1`는 subscribeOn(Schedulers.boundedElastic())을 통해서 새로운 Thread에서 작동 되도록 했기 때문에 생긴 것이다. 그리고 reactor-netty의 tcpClient에 의해서 `reactor-tcp-nio-1`가 동작하는 것을 확인하였다. 따라서 WebFlux를 사용하였다면 기본적으로(별도의 event loop group으로 지정하지 않는다면) WebFlux에서 사용되는 event loop을 같이 사용할텐데, `Spring Web MVC`에서는 이렇게 별도의 event loop이 작동하고 있는 것을 확인할 수 있다.

## Performance benchmark 결과 생각해보기

`Spring Web MVC + JDBC database driver`와 `Spring Web MVC + R2DBC driver`의 Performance 비교 글<sup>[1][1] </sup>에서 Response time은 `Spring Web MVC + R2DBC driver`가 부하가 커질수록 더 좋은 성능을 보였다. 그리고 throughput도 점점 부하가 커질 수록 `Spring Web MVC + R2DBC driver`가 좋다는 결과를 보였다.

🤔 어떻게 `Spring Web MVC + R2DBC driver`가 더 좋을까?

### Async Controller로 worker thread가 request 처리를 더 할 수 있다?

Servlet 3.0에서는 Asynchronous 기능이 생겼고, DefferedResult class를 통해서 Spring MVC에서 request를 비동기적으로 처리할 수 있게 되었다. Servelt 3.1에서는 Non-Blocking이 추가되었고, 따라서 Servlet 3.1+를 만족하는 servlet container는 WebFlux에서도 사용이 가능하다. Sample Project에서 embedded Tomcat 9.x가 사용되고 있고, 기본적으로 NIO Connector를 사용하게 된다. 과거 request per thread 모델인 BIO connector는 depreacted 되었다. NIO Connector로 이제 Acceptor가 socket accept를 담당하고, Poller가 이제 socket write와 read가 준비되면 worker thread에서 처리하도록 한다. 따라서 blocking I/O 때문에 thread가 불필요하게 Idle되는 시간을 줄인다.

NIO connector를 사용하기 때문에 아랫처럼 thread가 있는 것을 확인할 수 있다.
- http-nio-8080-exec-1
- http-nio-8080-Poller
- http-nio-8080-Acceptor

**(여기서부터는 글쓴이의 추측이 담겨 있는 내용입니다 🤪)**

Spring Web MVC에서 `ReactiveTypeHandler` class<sup>[4][4] </sup>이 존재하고, Flux와 Mono type도 handle할 수 있는 것으로 보인다. 그리고 `ReactiveTypeHandler`의 `handleValue` method가 `ResponseBodyEmitter`를 리턴하고 있다.

```java
public ResponseBodyEmitter handleValue
```

**따라서 Controller에 Flux와 Mono가 있으면 Async Controller처럼 작동하는 것이 아닐까?** 

`catRepository.getLimit`은 Flux type으로 이제 reactor-netty가 돌아가는 `reactor-tcp-nio-1`에서 emitter<sup>[5][5] </sup>로 send, complete로 작동하게 된다.

reactor-netty의 event loop에서 blocking 요소를 제거 하기 위해서 `subscribeOn(Schedulers.boundedElastic())`으로 별도 thread에서 complete될 때까지 buffer에 저장하여 List로 반환하도록 한다.

**database에서 데이터를 가져오는 것은 reactor-netty를 통해서 적은 thread로 가능하게 되고, request를 처리하는 worker thread는 반환해주니깐 throughput과 response time이 부하가 더 걸릴 수록 유리하게 되는것이 아닐까?** 그냥 JDBC를 사용했으면 worker thread가 Database로부터 결과값을 받아 올때까지 계속 잡혀있으니깐.

```kotlin
@GetMapping("/cats")
fun getCats() : MutableList<Cat>? {
    return catRepository
            .getLimit()
            .subscribeOn(Schedulers.boundedElastic())
            .buffer().blockLast()
}
```

## Back-pressure

reactive stream의 특징 중 하나는 back-pressure 기능이다. 🤔 **R2DBC에서는 back-pressure가 어떻게 작동할 수 있을까?** Database는 Client와 Server간에 어떤 패킷을 전달해야되는지 Protocol이 존재하고, TCP는 receive window, congestion control 등을 통해서 flow control을 하게 된다. 따라서 R2DBC driver가 이러한 특성을 통해서 back-pressure를 구현할 수 있다고 설명한다.<sup>[6][6]</sup>

Flink에서 어떻게 back-pressure가 작동하는지 설명하는 글<sup>[7][7]</sup>에서처럼 R2DBC driver도 Netty의 ByteBuf에서 socket buffer에서 읽어 오는 것을 Application에서 컨트롤하고, back pressure은 TCP/IP layer의 메카니즘에 의해서 작동하는 것 아닐까?


reactive stream은 아랫처럼 Publisher, Subscriber, Subscription를 만족하게 된다. request를 통해서 upstream에 얼마나 요청할지 결정하게 된다.

```java
public interface Publisher<T> {
    public void subscribe(Subscriber<? super T> s);
}

public interface Subscription {
    public void request(long n);
    public void cancel();
}

public interface Subscriber<T> {
    public void onSubscribe(Subscription s);
    public void onNext(T t);
    public void onError(Throwable t);
    public void onComplete();
}
```

앞서 MySQL과 Postgresql의 R2DBC driver에서 `ReactorNettyClient` class를 확인하였다. 이제 Database으로부터 데이터를 `BackendMessageSubscriber`를 통해서 받게 되는데, upstream에 request의 n이 1로 고정되어 있는 것을 debugging tool로 확인 할 수 있었다. `Spring Web MVC`에서 Webflux의 WebClient를 사용할 때의 다이어그램을 보면 request(1)로 나와있다.<sup>[6][6]</sup> `Spring Web MVC`에서 stream처럼 취급하여 request(1)로 하나씩 가져오도록 된 것으로 이해된다.

Database에 `Select`를 하면 Database는 해당 Query에 대한 데이터를 검색하고 전달하게 된다. Protocol을 통해서 해당 데이터를 패킷으로 보내고, R2DBC driver에서 user space에서 얼마나 읽을지 정한다. 필요가 없으면 이제 kernel space에 있는 data는 그냥 버려질 수 있다. 이렇게 버려지는 데이터가 없이 Database에서 request한 데이터만 받아오도록 하려면 Postgresql에서는 portal같은 cursor기능을 활용해야 되는 것 같다. (하지만 Database와 명령을 더 주고 받아야 하기 때문에 response time은 증가하지 않을까?)

## 결론

Spring Web MVC에서도 Flux, Mono를 handle할 수 있도록 되어 있다. reactive stream으로 작동하는 WebClient나 R2DBC driver를 이용하면, Controller에서 비동기처럼 작동하고 worker thread를 반환할 수 있다. JDBC driver를 통해서 blocking하게 작동하면, worker thread가 Database로부터 전체 결과값을 받아올 때까지 blocking되고 idle한 상태가 된다. 따라서 react-netty의 event loop으로 적은 thread로 Database와 통신을 하고, worker thread가 request를 더 처리할 시간을 준다. 그렇기 때문에 Performance 비교한 글처럼<sup>[1][1]</sup> `Spring Web MVC + R2DBC driver`가 response time과 throughput이 높은 부하에서 더 좋은 결과값을 나온 것으로 생각된다.

[1]: https://technology.amis.nl/software-development/performance-and-tuning/spring-blocking-vs-non-blocking-r2dbc-vs-jdbc-and-webflux-vs-web-mvc/

[2]: https://github.com/pgjdbc/r2dbc-postgresql

[3]: https://github.com/mirromutth/r2dbc-mysql

[4]: https://github.com/spring-projects/spring-framework/blob/main/spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/method/annotation/ReactiveTypeHandler.java

[5]: https://www.baeldung.com/spring-mvc-sse-streams

[6]: https://www.amazon.com/Hands-Reactive-Programming-Spring-cloud-ready-ebook/dp/B076QCBXZ2

[7]: https://www.codetd.com/en/article/12228335