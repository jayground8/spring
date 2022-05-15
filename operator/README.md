# Project Reactor

## create stream programmatically

### push vs create

push와 create는 동일하게 보이지만 아래와 같은 다른 점이 있다.

| push            | create                   |
|-----------------|--------------------------|
| single threaded | work with multi-threaded |

```java
// single-threaded
Flux<String> stream6 = Flux.push(emitter -> {
    emitter.next("one");
    emitter.next("two");
    emitter.next("three");
    emitter.complete();
});
```

```java
// multi-threaded
Flux<String> stream7 = Flux.create(emitter -> {
    emitter.next("one");
    emitter.next("two");
    emitter.next("three");
    emitter.complete();
});
```

소스 코드를 살펴보면, create는 CreateMode가 PUSH_PULL로 정의되어서 `SerializedFluxSink` 객체를 만든다. push는 `BufferAsyncSink`를 만든다. `SerializedFluxSink`는 multi thread에서 작동할 수 있도록 `BufferAsyncSink`를 감싸고 있다. source는 Consumer interface고 accept를 통해서 `SerializedFluxSink`나 `BufferAsyncSink`를 인자로 전달한다.

```java
source.accept(
        createMode == CreateMode.PUSH_PULL 
        ? new SerializedFluxSink<>(sink) 
        :sink
);
```

이제 `create`과 `push`로 생성된 Flux 객체에 subscribe를 하면 actual subscriber의 onSubscribe method를 call하고 인자로 BaseSink를 보낸다.

```java
@Override
public void subscribe(CoreSubscriber<? super T> actual){
    BaseSink<T> sink=createSink(actual,backpressure);

    actual.onSubscribe(sink);
    
    ...생략...
}
```

`FluxSink`의 next는 Queue에 담아 두고, Subscriber가 request로 요청을 하면 Queue에서 데이터를 가져와서 onNext로 Subscriber에 전달하게 된다. 

### generate

generate은 state를 가지고 있어서 이걸 가지고 다양한 로직을 만들 수 있다.

```java
Flux<Integer> stream8 = Flux.generate(
        () -> 10,
        (state, emitter) -> {
        int current = state + 1;
        emitter.next(current);
        if (current == 15) emitter.complete();
        return current;
        },
        (state) -> System.out.println("clean up :" + state));
```

`reactor-netty`에서 `generate`를 사용한 예가 있다. open된 file의 상태(`FileChannel.open(path)`)를 상태 `fc`로 가지면서, `fc`를 통해서 데이터를 쓴다. 그리고 완료가 되면 `ReactorNetty.fileCloser`가 이 state `fc`를 받아서 close하게 된다.

```java
Flux.generate(() -> FileChannel.open(path),
        (fc, sink) -> {
            ByteBuf buf = allocator.buffer();
            try {
                if (buf.writeBytes(fc, maxChunkSize) < 0) {
                    buf.release();
                    sink.complete();
                }
                else {
                    sink.next(buf);
                }
            }
            catch (IOException e) {
                buf.release();
                sink.error(e);
            }
            return fc;
        },
        ReactorNetty.fileCloser)
```

## Subscription

subscribe에서 lambda expression으로 onNext, onError, onComplete, onSubscribe에 대한 액션을 정의할 수 있다. 그런데 마지막 Subscription을 받는 부분은 deprecated 되었다. 유저가 Subscription으로 실수를 많이 하기 때문에 deprecated 되었다고 나온다.

```java
stream1.subscribe(
    e -> System.out.println(e),
    err -> err.printStackTrace(),
    () -> System.out.println("completed"),
    s -> s.request(4));
```

Custom Subscriber를 만들 때, Reactor의 TCK를 만족하도록 만드는 것은 까다롭기 때문에 `BaseSubscriber`와 같은 class를 상속받아서 작성하는 것이 추천된다.

```java
class CustomSubscriber<T> extends BaseSubscriber<T> {
    @Override
    protected void hookOnSubscribe(Subscription subscription) {
        request(1);
    }

    @Override
    protected void hookOnNext(T value) {
        System.out.println("value: " + value);
        request(1);
    }
};

CoreSubscriber<String> subscriber = new CustomSubscriber<>();
```

`reactor-netty`에서 `CoreSubscriber`를 사용한 경우를 찾을 수 있다. 이제 `onError`, `onComplete`, `cancel`처럼 종료되는 이벤트에 대해서 Channel을 닫도록 설정하였다.

```java
static final class ChannelDisposer extends BaseSubscriber<Void> {

    final DisposableChannel channelDisposable;
    ChannelDisposer(DisposableChannel channelDisposable) {
        this.channelDisposable = channelDisposable;
    }

    @Override
    protected void hookOnSubscribe(Subscription subscription) {
        request(Long.MAX_VALUE);
        // BaseSubscriber.dispose() -> hookFinally -> ChannelDisposable.dispose()
        channelDisposable.onDispose(this); 
    }

    // Optional hook executed after any of the termination events (onError, onComplete, cancel).
    @Override
    protected void hookFinally(SignalType type) {
        if (type != SignalType.CANCEL) {
            channelDisposable.dispose();
        }
    }
}
```

## empty & never & defer

`empty`는 `onNext` 없이 `onComplete` 시그널을 전달한다.

```java
Mono<Void> mono = Mono.empty();
Stream<Void> stream = Flux.empty();
```

```log
[ INFO] (main) onSubscribe([Fuseable] Operators.EmptySubscription)
[ INFO] (main) request(unbounded)
[ INFO] (main) onComplete()
```

`never`는 `onNext`와 `onComplete` 시그널 둘다 전달하지 않는다.

```java
Mono<Void> mono = Mono.never();
Stream<Void> stream = Flux.never();
```

```log
[ INFO] (main) onSubscribe([Fuseable] Operators.EmptySubscription)
[ INFO] (main) request(unbounded)
```

`empty`와 `never`이 주로 Test 과정에서 많이 사용되는 것으로 보인다. 하지만 Test 이외에도 사용될 수 있는데, error handling을 위해서 `onErrorResume`을 사용한다고 생각해보자. 데이터를 보낼 필요는 없고, `onComplete`에서 뭔가 정리하는 로직이 있다라고 하면 `Mono.empty()`를 사용할 수 있는 것이다.

```java
Mono.error(new RuntimeException("something wrong happened"))
        .onErrorResume(e -> Mono.empty())
        .subscribe();
```

defer는 이제 lazy하게 evaluation할 수 있도록 해준다. 아랫처럼 random하게 `Mono.error()`를 리턴하는 method가 있다라고 가정해보자.

```java
static Mono<String> requestUser(String input) {
    return isValidSession(input)
            ? Mono.fromCallable(() -> "user")
            : Mono.error(new RuntimeException("invalid user"));
}

static Boolean isValidSession(String sessionId) {
    Random rd = new Random();
    return rd.nextBoolean();
}
```

그럼 이제 requestUser method를 호출해서 `eagerMono`를 만들어서 여러 번 subscribe를 하는 코드를 작성한다. 이 경우에는 requestUser method가 호출되었을 때 random값에 의해서 `Mono.fromCallable`일지 `Mono.error`일지 결정된다. 따라서 네번의 subscribe의 결과는 동일하다. 하지만 우리는 매번 subscribe가 될 때마다 다른 Random값이 적용되길 원한다면 어떨까? requestUser가 API call을 하는 경우라면, 서버로 받아온 결과값을 제사용하는 것이 아니라 매번 subscribe할 때마다 다시 API call을 하고 싶으면 어떻게 해야 할까?

```java
Mono<String> eagerMono = requestUser("user");
eagerMono.subscribe(errorLogSubscriber);
eagerMono.subscribe(errorLogSubscriber);
eagerMono.subscribe(errorLogSubscriber);
eagerMono.subscribe(errorLogSubscriber);
```

defer를 사용할 수 있다. 이제 이렇게 defer와 lambda expression으로 작성을 하면, lazy하게 evaluation된다. 즉 subscribe을 해서 이것이 실행될 때, requestUser가 호출되게 된다. 따라서 네 개의 subscriber가 각각의 Random값에 의해서 다른 결과값을 보여주게 된다. 

```java
 Mono<String> lazyMono = Mono.defer(() -> requestUser("user"));

lazyMono.subscribe(errorLogSubscriber);
lazyMono.subscribe(errorLogSubscriber);
lazyMono.subscribe(errorLogSubscriber);
lazyMono.subscribe(errorLogSubscriber);
```

## delayElements

`reactor-kafka` source code에서는 아랫처럼 test code에 사용되는 것을 확인할 수 있다.

```java
kafkaSender
    .createOutbound()
    .send(
        createProducerRecords(count)
        .delayElements(Duration.ofMillis(100)))
    .then()
    .subscribe();
```

`reactor-netty`에서도 Test code에서 다양하게 사용하고 있다.

재미있는 것은 `delayElements`의 코드를 자세히 보면 `Mono.just`, `Mono.delayUntil`, `Mono.delay`등이 사용되어서 구성되어 있다.

```java
public final Flux<T> delayElements(Duration delay) {
    return delayElements(delay, Schedulers.parallel());
}
```

```java
public final Flux<T> delayElements(Duration delay, Scheduler timer) {
    return delayUntil(d -> Mono.delay(delay, timer));
}

public final Flux<T> delayUntil(Function<? super T, ? extends Publisher<?>> triggerProvider) {
    return concatMap(v -> Mono.just(v)
            .delayUntil(triggerProvider));
}
```

1. `Mono.delay()`를 통해서 별도의 thread에서 delay만큼 기다렸다가 onNext signal을 발생한다.
2. `Mono.delayUntil`를 통해서 `Mono.delay()`에서 onNext signal를 기다리고, signal을 받으면 `Mono.just(v)`에서 `v`를 onNext로 전달한다.
3. concatMap은 순서를 보장해서 `Mono.just()`로 만들어진 publisher를 하나의 Flux로 만든다.

따라서 `delayElements`를

```java
Flux.range(1, 10)
    .delayElements(Duration.ofSeconds(1))
    .subscribe();
```

아래처럼 풀어서 작성할 수 있다.

```java
Flux.range(1, 10)
    .concatMap(e -> Mono.just(e).delayUntil(d -> Mono.delay(Duration.ofSeconds(1), Schedulers.boundedElastic())))
    .log()
    .subscribe();
```

## backpressure

위에서 `Flux.create`과 `Flux.push`등으로 Stream을 생성할 때, `FluxSink.next()`로 내려주는 값을 먼저 Queue에다가 쌓아 놓는 것을 확인하였다. 그리고 Consumer가 request하는 만큼 Queue에서 빼와서 전달을 하게 된다. Consumer가 slow consumer일 경우 Queue에 쌓일 수 있는데, 이것에 대해서 전략을 여러가지 제공한다. 기본으로는 Buffer 방식으로 작동한다.

마찬가지로 operator를 통해서도 아래와 같이 제공한다. 

- onBackPressureBuffer
- onBackPressureDrop
- onBackPressureLast
- onBackPressureError

아래의 예제로 한번 더 자세히 살펴보자.

```java
Flux.interval(Duration.ofMillis(1))
    .log()
    .onBackpressureDrop(e -> System.out.println("dropped: " + e))
    .log()
    .concatMap(a -> Mono.delay(Duration.ofMillis(10)).thenReturn(a))
    .doOnNext(a -> System.out.println("Element kept by consumer: " + a))
    .blockLast();
```

`onBackpressureDrop`은 upstream에 unbounded request를 하게 되고, downstream으로 보내는 것은 request(n)에 따라서 n에 만큼 전달하게 된다.

```
Flux.interval() <--request(unbounded)-- onBackpressureDrop <--request(n)-- concatMap
```

따라서 `Flux.interval(Duration.ofMillis(1))`는 1ms마다 downstream으로 onNext로 데이터를 보내게 된다. onBackpressureDrop은 이제 concatMap으로부터 request(n)을 받는데, concatMap으로 n개 만큼 onNext() 보내게 된다. concatMap이 10ms의 delay를 가지기 때문에, onBackpressureDrop이 onNext()로 n개를 downstream로 보내기전에 upstream인 Flux.interval()이 onNext로 내리게 된다. 이렇게 보내지는 데이터는 drop이 되는 것이다.

```
Flux.interval() --onNext()--> onBackpressureDrop --onNext()-- concatMap
```

내부적인 코드를 살펴보면 이처럼 volatile long type의 property가 있고,

```java
volatile long requested;
static final AtomicLongFieldUpdater<Sample> REQUESTED =
        AtomicLongFieldUpdater.newUpdater(Sample.class, "requested");
```

downstream에서 request한 n만큼을 max값으로 설정하고,

```java
Operators.addCap(REQUESTED, this, n);
```

dowanstream으로 onNext통해서 data를 내려주면 -1씩을 한다.

```java
 Operators.produced(REQUESTED, this, 1);
```

requested가 0이면 이제 그다음부터는 onNext로 전달되지 않고 drop되게 된다.

```java
if (r != 0L) {
    actual.onNext(t);
    if(r != Long.MAX_VALUE) {
        Operators.produced(REQUESTED, this, 1);
    }
}
else {
    try {
        onDrop.accept(t);
    }
    catch (Throwable e) {
        onError(Operators.onOperatorError(s, e, t, ctx));
    }
    Operators.onDiscard(t, ctx);
}
```

### limitRate

위에서 본 것과 동일하게 slow consumer를 만들고, limitRate operator를 사용해보자.

```
Flux.interval(Duration.ofMillis(1))
    .log()
    .limitRate(100)
    .concatMap(a -> Mono.delay(Duration.ofMillis(10)).thenReturn(a))
    .doOnNext(a -> System.out.println("Element kept by consumer: " + a))
    .subscribe(System.out::println);
```

limitRate를 통해서 이제 upstream에 100 n만큼 request를 한다. 하지만 Flux.interval이 이미 100개를 보내고 또 보낼려고 하면 Overflow Exception이 발생한다.

```
Flux.interval <--request(100)-- limitRate(100)
```

## Reactor Context

이제 Stream pipeline이 있으면 이제 다른 Thread들에서 실행될 수 있다. Reactor Context는 이제 이렇게 다른 Thread에서 실행되더라도 Reactor Context로 데이터를 공유할 수 있다. (subscriberContext is deprecated)

```java
String key = "message";
Mono<String> r = Mono.just("Hello")
                .flatMap(s -> Mono.deferContextual(ctx ->
                        Mono.just(s + " " + ctx.get(key))))
                .contextWrite(ctx -> ctx.put(key, "World"));
```

```java
String num = "num";
Flux.just("one", "two")
        .transformDeferredContextual((original, ctx) -> 
            original.map(e -> e + " " + ctx.get(num)))
        .contextWrite(ctx -> ctx.put(num, 3))
        .subscribe(System.out::println);

```
