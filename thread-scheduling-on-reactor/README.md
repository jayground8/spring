# publishOn / subscribeOn

## Reactive Stream Life cycle

Reactive stream life cycle은 이렇게 세 단계로 생각할 수 있다.
- assembly time
- subscription time
- runtime

아래의 코드로 각 life cycle를 생각해보자.

```java
Flux.just("one", "two").log().map(e -> e + "-map").log().subcribe(customSubscriber);
```

### 1. assembly time

- `just`: `FluxArray` class를 사용
- `map`: `FluxMap` class 사용

```
FluxMap(
    FluxArray("one", "two")
)
```

`FluxArray`와 `FluxMap` 모두 Publish interface를 구현하고 있다. FluxMap이 wrapper처럼 property로 `FluxArray` object reference를 가지고 있는다. 아래처럼 생성자로 source를 인자로 받는다.

`FluxMap constructor`
```
FluxMap(Flux<? extends T> source,
        Function<? super T, ? extends R> mapper) {
    super(source);
}
```

### 2. subscription time

`FluxMap`이 `FluxArray`를 감싸고 있고, `FluxMap`의 subscribe를 call하면 subscriber를 감싼 `MapSubsriber`를 `FluxArray`에 subscribe한다. 최종적으로 `FluxArray`는 `MapSubscriber`를 감싼 `ArraySubscription`을 subscriber에 onSubscribe method로 전달한다.

```
ArraySubscription(
    MapSubscriber(
        Subscriber
    )
)
```

### 3. runtime

이제 실제로 request를 하는 시점이다. subscriber는 subscription 객체를 onSubscribe method의 인자로 전달 받았다. 그리고 해당 subscription 객체의 request method로 데이터를 publisher로부터 요청한다.

ArraySubscription의 request를 콜하면, ["one", "two"]를 MapSubscriber의 onNext로 전달한다. MapSubscriber는 mapper로 `e -> e + "-map"`를 가지고 있고, onNext method에서 해당 mapper를 call하여 데이터를 변환하고 최종적으로 subscriber onNext로 변환된 데이터를 전달한다.

```
Subscriber - request(ArraySubscription) -> Publisher
```

```
Publisher - onNext -> MapSubsriber -> onNext -> Subscriber
```

## subscribeOn

아래의 코드는 FluxArray 객체를 reference로 가지고 있는 FluxMap Publisher 객체가 된다. 즉 `Flux<String>`이 최종적으로 되었다.

```java
Flux.just("one", "two").map(e -> e + "-map")
```

subscribe 과정을 다시 생각해보면 아랫처럼 진행된다.

1. FluxMap.subscribe(MapSubscriber(Subscriber))
2. FluxArray.subscribe(ArraySubscription(MapSubscriber))
3. Subscriber.onSubscribe(ArraySubscription)

아래의 코드를 돌려서 log를 확인하면, main thread에서 모두 실행이 된 것을 확인할 수 있다.
```java
Flux.just("one", "two").log().subscribe(System.out::println);
```

```log
[ INFO] (main) | onSubscribe([Synchronous Fuseable] FluxArray.ArraySubscription)
[ INFO] (main) | request(unbounded)
[ INFO] (main) | onNext(one)
one
[ INFO] (main) | onNext(two)
two
[ INFO] (main) | onComplete()
```

`subscribeOn`은 이제 subscribe를 다른 thread에서 실행될 수 있도록 할 수 있다. 아래의 코드처럼 실행하면 다른 thread에서 실행되는 것을 볼 수 있다.

```java
Flux.just("one", "two")
    .log()
    .subscribeOn(Schedulers.boundedElastic())
    .subscribe(System.out::println);
```

```log
[ INFO] (boundedElastic-1) | onSubscribe([Synchronous Fuseable] FluxArray.ArraySubscription)
[ INFO] (boundedElastic-1) | request(unbounded)
[ INFO] (boundedElastic-1) | onNext(one)
one
[ INFO] (boundedElastic-1) | onNext(two)
two
[ INFO] (boundedElastic-1) | onComplete()
```

## publishOn

`publishOn`은 이제 publisher가 subscriber에게 data를 내려주는 시점에 다른 Thread에서 실행할 수 있도록 도와준다. 그리고 그 경계에는 Queue를 통해서 enqueue, dequeue를 하게 된다.

아래처럼 `publishOn`과 `subscribeOn` 둘 다 없이 실행하면, main thread에서 실행된다.

```java
Flux.just("one", "two").map(e -> e + "-map").log().subscribe(System.out::println);
```

```log
[ INFO] (main) | onSubscribe([Fuseable] FluxMapFuseable.MapFuseableSubscriber)
[ INFO] (main) | request(unbounded)
[ INFO] (main) | onNext(one-map)
one-map
[ INFO] (main) | onNext(two-map)
two-map
[ INFO] (main) | onComplete()
```

이번에는 `just`와 `map` 사이에 `publishOn`을 설정해보자. 그러면 MapSubscriber에 onNext의 시점에 별도의 thread에서 작동하는 것을 확인할 수 있다.

```
Publisher - *onNext* -> MapSubsriber -> onNext -> Subscriber
```

```java
Flux.just("one", "two")
    .publishOn(Schedulers.boundedElastic())
    .map(e -> e + "-map")
    .log()
    .subscribe(System.out::println);
```

```log
[ INFO] (main) | onSubscribe([Fuseable] FluxMapFuseable.MapFuseableSubscriber)
[ INFO] (main) | request(unbounded)
[ INFO] (boundedElastic-1) | onNext(one-map)
one-map
[ INFO] (boundedElastic-1) | onNext(two-map)
two-map
[ INFO] (boundedElastic-1) | onComplete()

```

그럼 아랫처럼 `publishOn`을 추가하면 어떻게 될까? MapSubscriber에서 mapper를 call해서 변환한 데이터를 actual Subscriber에 onNext로 전달하는 것이 single thread에서 작동되게 된다.

```
Publisher - *onNext* -> MapSubsriber -> *onNext* -> Subscriber
```

```java
Flux.just("one", "two")
    .publishOn(Schedulers.boundedElastic())
    .log()
    .map(e -> e + "-map")
    .publishOn(Schedulers.single())
    .log()
    .subscribe(System.out::println);
```

```log
[ INFO] (main) | onSubscribe([Fuseable] FluxPublishOn.PublishOnSubscriber)
[ INFO] (main) | onSubscribe([Fuseable] FluxPublishOn.PublishOnSubscriber)
[ INFO] (main) | request(unbounded)
[ INFO] (main) | request(256)
[ INFO] (boundedElastic-1) | onNext(one)
[ INFO] (boundedElastic-1) | onNext(two)
[ INFO] (single-1) | onNext(one-map)
one-map
[ INFO] (single-1) | onNext(two-map)
[ INFO] (boundedElastic-1) | onComplete()
two-map
[ INFO] (single-1) | onComplete()
```

최종적으로 이제 `subscribeOn`과 `publishOn`를 같이 사용하면, subscribe 시점에 main thread가 아니라 다른 thread에서 실행되는 것을 확인 할 수 있다.

```java
Flux.just("one", "two")
    .publishOn(Schedulers.boundedElastic())
    .log()
    .map(e -> e + "-map")
    .publishOn(Schedulers.single())
    .log()
    .subscribeOn(Schedulers.parallel())
    .subscribe(System.out::println);
```

```log
[ INFO] (parallel-1) | onSubscribe([Fuseable] FluxPublishOn.PublishOnSubscriber)
[ INFO] (parallel-1) | onSubscribe([Fuseable] FluxPublishOn.PublishOnSubscriber)
[ INFO] (parallel-1) | request(unbounded)
[ INFO] (parallel-1) | request(256)
[ INFO] (boundedElastic-1) | onNext(one)
[ INFO] (boundedElastic-1) | onNext(two)
[ INFO] (single-1) | onNext(one-map)
one-map
[ INFO] (single-1) | onNext(two-map)
two-map
[ INFO] (boundedElastic-1) | onComplete()
[ INFO] (single-1) | onComplete()
```

마지막으로 아랫처럼 `subscribeOn`를 두번 작성하면 어떻게 될까? 위에서 설명한 것처럼 subscribe의 flow를 생각해보자. 

1. FluxMap.subscribe(MapSubscriber(Subscriber))
2. FluxArray.subscribe(ArraySubscription(MapSubscriber))
3. Subscriber.onSubscribe(ArraySubscription)

```java
Flux.just("one", "two")
    .log()
    .subscribeOn(Schedulers.single()) // FlaxArray
    .map(e -> e + "-map")
    .log()
    .subscribeOn(Schedulers.parallel()) // FluxMap
    .subscribe(System.out::println);
```

```log
[ INFO] (parallel-1) onSubscribe(FluxMap.MapSubscriber)
[ INFO] (parallel-1) request(unbounded)
[ INFO] (single-1) | onSubscribe([Synchronous Fuseable] FluxArray.ArraySubscription)
[ INFO] (single-1) | request(unbounded)
[ INFO] (single-1) | onNext(one)
[ INFO] (single-1) onNext(one-map)
one-map
[ INFO] (single-1) | onNext(two)
[ INFO] (single-1) onNext(two-map)
two-map
[ INFO] (single-1) | onComplete()
[ INFO] (single-1) onComplete()
```

## fromCallable & subscribeOn

이번에는 `fromCallable`로 Mono를 생성해보는 것을 생각해보자. 아래 코드는 Lambda 형식으로 call이 되었을 때, 해당 Thread를 5초동안 blocking하도록 하였다.

```java
Mono<String> mono1 = Mono.fromCallable(() -> {
    Thread.sleep(5000L);
    return "data"
})
```

`fromCallable`은 MonoCallable 객체를 만들게 되는데, `MonoCallable`의 subscribe는 아랫처럼 작성되어 있다. 중요한 것은 `T t = callable.call()`에서 보는 것처럼 call을 subscribe안에서 하고 있다.

```java
@Override
public void subscribe(CoreSubscriber<? super T> actual) {
    Operators.MonoSubscriber<T, T>
            sds = new Operators.MonoSubscriber<>(actual);

    actual.onSubscribe(sds);

    if (sds.isCancelled()) {
        return;
    }

    try {
        T t = callable.call();
        if (t == null) {
            sds.onComplete();
        }
        else {
            sds.complete(t);
        }
    }
    catch (Throwable e) {
        actual.onError(Operators.onOperatorError(e, actual.currentContext()));
    }

}
```

`subscribeOn`없이 아랫처럼 실행하면 main thread에서 실행이 되기 때문에, call되었을 때 해당 thread가 5초동안 blocking이 된다.

```java
Mono<String> mono1 = Mono.fromCallable(() -> {
    Thread.sleep(5000L);
    return "data";
});
mono1.log().subscribe();
Flux.just(1,2,3).subscribe(System.out::println);
```

```log
[ INFO] (main) | onSubscribe([Fuseable] Operators.MonoSubscriber)
[ INFO] (main) | request(unbounded)
[ INFO] (main) | onNext(data)
[ INFO] (main) | onComplete()
1
2
3
```

하지만 이번에는 `subscribeOn`으로 subscribe를 다시 thread에서 실행하도록 하면, `Flux.just`로 생성된 stream은 main thread에서 blocking없이 바로 실행될 수 있다.

```java
Mono<String> mono1 = Mono.fromCallable(() -> {
    Thread.sleep(5000L);
    return "data";
});
mono1.log().subscribeOn(Schedulers.boundedElastic()).subscribe();
Flux.just(1,2,3).subscribe(System.out::println);
```

```log
1
2
3
[ INFO] (boundedElastic-1) | onSubscribe([Fuseable] Operators.MonoSubscriber)
[ INFO] (boundedElastic-1) | request(unbounded)
[ INFO] (boundedElastic-1) | onNext(data)
[ INFO] (boundedElastic-1) | onComplete()
```

## 결론

`subscribeOn`과 `publishOn`의 차이점이 헛갈릴 수 있다. Reactive stream의 lifecycle의 3단계(assembly-time, subscription-time, runtime)을 이해하고, subscribe하는 시점과 데이터를 내려주는 시점을 구분하면 더 명확하게 이해할 수 있을 것이다.