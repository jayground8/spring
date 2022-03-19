# Spring MVC에서 R2DBC 사용

`Spring Web MVC + JDBC database driver`와 `Spring Web MVC + R2DBC driver`의 Performance 비교<sup>[1][1]</sup>에 관한 글을 보았다. 🤔 **blocking이 되는 Spring Web MVC에서 Non-blocking R2DBC driver를 사용하는 것이 어떠한 이득이 될지 궁금해졌다.**

## R2DBC SPI

R2DBC는 Service Provider Interface를 제공하고 있다. Driver를 개발하는 쪽에서 이 SPI에 따라서 작성을 해야된다. 당연히 Driver는 Non-blocking하게 작동해야 한다.

## Driver

Driver는 R2DBC specification에 맞춰서 구현되고, Project Reactor, RxJava와 같은 Reactive Stream API를 포함하게 된다. 🤔 **Driver는 어떻게 비동기적으로 작동할 수 있을까?** 많은 드라이버가 Netty를 이용하여 client를 만들고 있다.

## Reactor-Netty

Netty는 Event Loop을 통해서 Non-Blocking & Async하게 작동하게 된다. Reactor Netty는 이제 Netty위에서 Back-Pressure가 가능한 server를 구성할 수 있다. 🤔 **WebFlux를 Netty와 함께 구성하면 사용할 수 있는 Event loop이 이미 있을텐데, Spring Web MVC에서는 어떻게 되는건지 궁금해졌다.**  

## r2dbc-postgresql

r2dbc-postgresql Github source code<sup>[2][2]</sup>를 살펴보았다. code에서 `ReactorNettyClient` 클래스를 찾을 수 있고, netty의 `TcpClient`를 사용하여 connection을 맺도록 되어 있다. 따라서 `reactor-netty`가 dependency로 있는 것을 확인할 수 있다.

## r2dbc-mysql
r2dbc-mysql Github source code<sup>[3][3]</sup>를 보면 마찬가지로 `ReactorNettyClient` 클래스가 있고, netty의 `TcpClient`를 이용하고 있는 것을 확인 할 수 있다.

## Sample code로 Thread 확인

Spring Web MVC에 r2dbc-postgresql를 사용하여 thread가 어떻게 사용되는지 확인해보았다. Sample project<sup>[4][4]</sup>는 `CommandLindRunner`를 통해서 실행하도록 간단하게 만들었다. R2dbcRepository를 통해서 catRepository를 만들고 `Spring Web MVC + JDBC database driver`와 `Spring Web MVC + R2DBC driver`의 Performance 비교 글<sup>[1][1]</sup>처럼 `buffer().blockLast()`로 blocking하도록 작성하였다. 이렇게 blocking한 부분은 다른 Thread에서 실행될 수 있도록 `subscribeOn`을 설정하였다.

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

`Spring Web MVC + JDBC database driver`와 `Spring Web MVC + R2DBC driver`의 Performance 비교 글<sup>[1][1]</sup>에서 Response time은 `Spring Web MVC + R2DBC driver`가 부하가 커질수록 더 좋은 성능을 보였다. 그리고 throughput도 점점 부하가 커질 수록 `Spring Web MVC + R2DBC driver`가 좋다는 결과를 보였다.

🤔 어떻게 `Spring Web MVC + R2DBC driver`가 더 좋을까?

### Servlet 3.1+

Servlet 3.0에서는 Asynchronous 기능이 생겼고, DefferedResult class를 통해서 Spring MVC에서 request를 비동기적으로 처리할 수 있게 되었다. Servelt 3.1에서는 Non-Blocking이 추가되었고, 따라서 Servlet 3.1+를 만족하는 servlet container는 WebFlux에서도 사용이 가능하다. Sample project<sup>[4][4]</sup>에서 embedded Tomcat 9.x가 사용되고 있고, 기본적으로 NIO Connector를 사용하게 된다. 과거 request per thread 모델인 BIO connector는 depreacted 되었다. NIO Connector로 이제 Acceptor가 socket accept를 담당하고, Poller가 이제 socket write와 read가 준비되면 worker thread에서 처리하도록 한다. 따라서 blocking I/O 때문에 thread가 불필요하게 Idle되는 시간을 줄인다.

NIO connector를 사용하기 때문에 아랫처럼 thread가 있는 것을 확인할 수 있다.
- http-nio-8080-exec-1
- http-nio-8080-Poller
- http-nio-8080-Acceptor

### Async Controller

Spring MVC 버전 3부터는 Servlet 3.0 Asyncrhonous가 지원되기 시작했다.<sup>[5][5]</sup> 따라서 Controller에서 `DefferedResult`를 리턴하도록 하면 Worker thread는 thread pool에 반환되고 다른 thread에서 `DefferedResult`의 `setResult()`로 Async하게 response를 완료할 수 있다.

```kotlin
@GetMapping("/sample")
@ResponseBody
fun quotes(): DeferredResult<String> {
	val deferredResult = DeferredResult<String>()
	// Save the deferredResult somewhere..
	return deferredResult
}

// From some other thread...
deferredResult.setResult(result)
```

`DefferedResult`와 비슷하게 `ResponseBodyEmitter`를 활용할 수 있다.<sup>[6][6]</sup>

```kotlin
@GetMapping("/sample")
fun handle() = ResponseBodyEmitter().apply {
	// Save the emitter somewhere..
}

// In some other thread
emitter.send("Hello World!")
emitter.send("Hello World2!")
emitter.complete()
```

### Spring Web MVC에서 Flux와 Mono 타입 핸들링

Spring Web MVC에서 `ReactiveTypeHandler` class<sup>[7][7]</sup>가 존재하고 Flux와 Mono type을 handle할 수 있다. 따라서 아랫처럼 return type이 Flux 타입이면 `DefferedResult`나 `ResponseBodyEmitter`처럼 비동기적으로 처리가 된다. 

```kotlin
@GetMapping("/flux")
fun getFlux() : Flux<Cat> {
    return catRepository.getLimit()
}
```

`ReactiveTypeHandler`에서 `handleValue` 메소드를 살펴보자. `curl`로 해당 path로 request를 하면 `handleValue` 안에서 아래와 같은 `DeferredResult`를 사용하도록 되어 있다.

```java
DeferredResult<Object> result = new DeferredResult<>();
new DeferredResultSubscriber(result, adapter, elementType).connect(adapter, returnValue);
WebAsyncUtils.getAsyncManager(request).startDeferredResultProcessing(result, mav);

return null;
```

`curl`에서 `Accept` header를 `application/stream+json`을 설정해서 request를 하게 되면 아랫처럼 `ResponseBodyEmitter`를 통해서 작동하게 되는 것을 확인할 수 있다.

```java
for (MediaType type : mediaTypes) {
    for (MediaType streamingType : JSON_STREAMING_MEDIA_TYPES) {
        if (streamingType.includes(type)) {
            logExecutorWarning(returnType);
            ResponseBodyEmitter emitter = getEmitter(streamingType);
            new JsonEmitterSubscriber(emitter, this.taskExecutor).connect(adapter, returnValue);
            return emitter;
        }
    }
}
```

`DefferedResultSubcriber`와 `JsonEmitterSubscriber`는 `AbstractEmitterSubscriber`를 상속받았고, `connect` 메소드는 Publisher에 subscribe하는 로직을 가지고 있다.

```java
public void connect(ReactiveAdapter adapter, Object returnValue) {
    Publisher<Object> publisher = adapter.toPublisher(returnValue);
    publisher.subscribe(this);
}
```

#### JsonEmitterSubscriber

`application/stream+json`으로 요청했을 때는 `JsonEmitterSubscriber`로 연결되는 것을 확인하였다. Publisher - Subscriber 관계속에서 Subscription request를 통해서 Publisher에 요청을 하게 되고, emitter.send를 통해서 client에 전달될 수 있다. 그리고 request로 요청하는 n이 1로 고정되어 있는 것을 확인할 수 있다. 이렇게 Spring Web MVC에서 Flux를 Async하게 작동하는 stream처럼 처리할 수 있게 된다.

```java
send(element);
this.subscription.request(1);
```

#### DefferedResultSubcriber

이제 기본적으로 media type이 stream이 아니면 `DefferedResultSubscriber`로 연결되는데, 아랫처럼 CollectedValuesList인 value에 `add`를 계속하다가 `onComplete`에서 최종적으로 `setResult`하여 client에 비동기적으로 전달한다. `request(Long.MAX_VALUE)`로 `push only`방식으로 동작하도록 되어 있다. backpressure 메카니즘이 작동하지 않는다.

```java
@Override
public void onSubscribe(Subscription subscription) {
    this.result.onTimeout(subscription::cancel);
    subscription.request(Long.MAX_VALUE);
}

@Override
public void onNext(Object element) {
    this.values.add(element);
}

@Override
public void onComplete() {
    if (this.values.size() > 1 || this.multiValueSource) {
        this.result.setResult(this.values);
    }
    else if (this.values.size() == 1) {
        this.result.setResult(this.values.get(0));
    }
    else {
        this.result.setResult(null);
    }
}
```
|Reactive Type| Spring MVC에서 변환 | 
|---|---|
|Mono| DefferedResult|
|Flux / non-streaming| DefferedResult<Lit<T>>|
|Flux / streaming|ResponseBodyEmitter with backpressure|

### 중간 생각

이렇게 Controller에서 `Flux`나 `Mono` 타입을 리턴하게 되면, 이제 DefferedResult나 ResponseBodyEmitter로 작동하게 되는 것을 확인하였다. 이제 worker thread는 pool에 반환되고 이제 `Flux`가 `reactor-netty`의 event loop안에서 비동기적으로 stream을 전달해줄거고, 그리고 이제 최종적으로 다른 worker thread에서 client에 response를 전달한다. 그렇기 때문에 worker thread가 Database query를 하고 idle 상태로 blocking되지 않는다는 장점이 있을 것이다.

### Performance benchmark code

 `Spring Web MVC + JDBC database driver`와 `Spring Web MVC + R2DBC driver`의 Performance 비교 글<sup>[1][1]</sup>의 테스트 방법은 `getAllPersons`에 HTTP GET request 부하를 주도록 되어 있다. 
 
 `getAllPersons`의 로직을 살펴보면 `Flux.buffer()`를 통해서 새롭게 subcription하여 List로 저장하고, stream이 complete될 때까지 계속 blocking하도록 되어 있다. 

```java
@GetMapping
private List<Person> getAllPersons() {
    return personRepository.findAll().subscribeOn(Schedulers.boundedElastic()).buffer().blockLast();
}
```

Debug Log를 보면 `nio-8080-exec-6 -> oundedElastic-2 -> actor-tcp-nio-1 -> nio-8080-exec-6`로 thread가 실행되는 것을 확인할 수 있다.

```
[nio-8080-exec-6] o.s.web.servlet.DispatcherServlet: GET "/cats", parameters={}
[nio-8080-exec-6] s.w.s.m.m.a.RequestMappingHandlerMapping : Mapped to com.example.demo.RestController#getCats()


[oundedElastic-2] o.s.d.r2dbc.core.NamedParameterExpander  : Expanding SQL statement [SELECT * FROM cats LIMIT 10] to [SELECT * FROM cats LIMIT 10]
[oundedElastic-2] io.r2dbc.pool.ConnectionPool             : Obtaining new connection from the pool
...
[oundedElastic-2] i.r.p.client.ReactorNettyClient          : [cid: 0x4][pid: 2246] Request:  Query{query='SELECT * FROM cats LIMIT 10'}


[actor-tcp-nio-1] i.r.p.client.ReactorNettyClient          : [cid: 0x4][pid: 2246] Response: RowDescription{...}
[actor-tcp-nio-1] i.r.p.client.ReactorNettyClient          : [cid: 0x4][pid: 2246] Response: DataRow{}
...
[actor-tcp-nio-1] i.r.p.client.ReactorNettyClient          : [cid: 0x4][pid: 2246] Response: ReadyForQuery{transactionStatus=IDLE}
[actor-tcp-nio-1] io.r2dbc.pool.PooledConnection           : Releasing connection
reactor-tcp-nio-1


[nio-8080-exec-6] m.m.a.RequestResponseBodyMethodProcessor : Using 'application/json', given [*/*] and supported [application/json, application/*+json, application/json, application/*+json]
[nio-8080-exec-6] m.m.a.RequestResponseBodyMethodProcessor : Writing [...]
[nio-8080-exec-6] o.s.web.servlet.DispatcherServlet        : Completed 200 OK
```

위에서 본 것처럼 Controller에서 `Flux`와 `Mono`를 리턴하지 않고 `List`를 리턴하고 있다. 따라서 ReactiveTypeHandler에 의해서 비동기적으로 처리되지 않는다.

🤔 **blockLast()에 의해서 `getAllPersons`를 처리하는 container thread가 같이 blocking된다. Async Controller처럼 worker thread가 database query때문에 idle되는 시간이 줄어드는 것도 아닌데, 트래픽 부하가 커질 때 어떻게 JDBC driver보다 더 좋은 성능을 보일 수 있었던걸까?**

🤔 다른 가정은 reactor-netty에서 Channel과 Event Loop을 통해서 Database의 데이터를 받아오는 것이 JDBC driver보다 효율적인건가?

## Back-pressure

reactive stream의 특징 중 하나는 back-pressure 기능이다. 🤔 **R2DBC에서는 back-pressure가 어떻게 작동할 수 있을까?** Database는 Client와 Server간에 어떤 패킷을 전달해야되는지 Protocol이 존재하고, TCP는 receive window, congestion control 등을 통해서 flow control을 하게 된다. 따라서 R2DBC driver가 이러한 특성을 통해서 back-pressure를 구현할 수 있다고 설명한다.<sup>[8][8]</sup>

Flink에서 어떻게 back-pressure가 작동하는지 설명하는 글<sup>[9][9]</sup>에서처럼 R2DBC driver도 Netty의 ByteBuf에서 socket buffer에서 읽어 오는 것을 Application에서 컨트롤하고, back pressure은 TCP/IP layer의 메카니즘에 의해서 작동하는 것 아닐까?


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

Database에 `Select`를 하면 Database는 해당 Query에 대한 데이터를 검색하고 전달하게 된다. Protocol을 통해서 해당 데이터를 패킷으로 보내고, R2DBC driver에서 user space에서 얼마나 읽을지 정한다. 필요가 없으면 이제 kernel space에 있는 data는 그냥 버려질 수 있다. 이렇게 버려지는 데이터가 없이 Database에서 request한 데이터만 받아오도록 하려면 Postgresql에서는 portal같은 cursor기능을 활용해야 되는 것 같다. (하지만 Database와 명령을 더 주고 받아야 하기 때문에 response time은 증가하지 않을까?)

## 결론

"Guide to "Reactive" for Spring MVC Developers" 발표<sup>[10][10]</sup>에서 설명한 것처럼 main thread 혹은 worker thread에서 복수의 I/O 작업을 하는 로직이 있을 때, Non-Blocking으로 작동하는 `WebClient`나 `R2DBC`를 사용하는 것이 `RestTemplate`이나 `JDBC`를 사용하는 것보다 효율적일 것이다.

JDBC의 경우 worker thread pool이 saturated되면 queue에 쌓여서 기다려야 한다. 따라서 Controller에서 Flux나 Mono를 리턴하도록 작성하여 테스트한 결과라면 Async Controller로 작동하여 worker thread가 반환될 수 있고, 그렇기 때문에 Database data를 받느라 thread가 blocking되는 것보다 좋은 성능 결과가 나올 수 있었을거라 생각했을 것이다. 

하지만 `Spring Web MVC + JDBC database driver`와 `Spring Web MVC + R2DBC driver`의 Performance 비교 글<sup>[1][1]</sup>에서는 `Flux.buffer().blockLast()`으로 작성하여 테스트를 하고 있다. 따라서 request를 처리하는 worker thread가 동일하게 blocking 될 것이고, 위와 같은 기대를 할 수가 없다.

R2DBC를 사용한다면 WebFlux를 사용하는 것이 좋겠지만, Spring MVC에서 R2DBC를 사용해야 된다면 Controller에서 Flux와 Mono를 리턴하도록 작성하는 것이 stream이 끝날때까지 block하는 것보다 바람직하지 않을까 한다.

[1]: https://technology.amis.nl/software-development/performance-and-tuning/spring-blocking-vs-non-blocking-r2dbc-vs-jdbc-and-webflux-vs-web-mvc/

[2]: https://github.com/pgjdbc/r2dbc-postgresql

[3]: https://github.com/mirromutth/r2dbc-mysql

[4]: https://github.com/jayground8/spring/tree/main/r2dbc/spring-boot-mvc-r2dbc-kotlin

[5]: https://spring.io/blog/2012/05/07/spring-mvc-3-2-preview-introducing-servlet-3-async-support

[6]: https://github.com/spring-projects/spring-framework/blob/main/spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/method/annotation/ReactiveTypeHandler.java

[7]: https://www.baeldung.com/spring-mvc-sse-streams

[8]: https://www.amazon.com/Hands-Reactive-Programming-Spring-cloud-ready-ebook/dp/B076QCBXZ2

[9]: https://www.codetd.com/en/article/12228335

[10]: https://www.infoq.com/presentations/spring-reactive-webflux/

## 참고자료

1: https://technology.amis.nl/software-development/performance-and-tuning/spring-blocking-vs-non-blocking-r2dbc-vs-jdbc-and-webflux-vs-web-mvc/

2: https://github.com/pgjdbc/r2dbc-postgresql

3: https://github.com/mirromutth/r2dbc-mysql

4: https://github.com/jayground8/spring/tree/main/r2dbc/spring-boot-mvc-r2dbc-kotlin

5: https://spring.io/blog/2012/05/07/spring-mvc-3-2-preview-introducing-servlet-3-async-support

6: https://github.com/spring-projects/spring-framework/blob/main/spring-webmvc/src/main/java/org/springframework/web/servlet/mvc/method/annotation/ReactiveTypeHandler.java

7: https://www.baeldung.com/spring-mvc-sse-streams

8: https://www.amazon.com/Hands-Reactive-Programming-Spring-cloud-ready-ebook/dp/B076QCBXZ2

9: https://www.codetd.com/en/article/12228335

10: https://www.infoq.com/presentations/spring-reactive-webflux/