import org.reactivestreams.Subscription;
import reactor.core.CoreSubscriber;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.ConnectableFlux;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

public class Example {
    public static void main(String[] args) throws InterruptedException{
//        createStream();
//        subscribing();
//        emptyOfNever();
//        defer();
//        operator();
//        combining();
//        reactorContext();
//        hotStream2();
//        errorHandling();
        backpressureStrategy();

//        Flux.just(7, 8, 9).log().delayElements(Duration.ofSeconds(2)).log().subscribe(System.out::println);
//
//        Flux.just(1, 2, 3).log().subscribe(System.out::println);
//        Flux.just(4, 5, 6).log().subscribe(System.out::println);

//        Flux<Integer> f1 = Flux.range(1, 3);
//        Flux<Integer> f2 = Flux.range(4, 3);
//        Flux<Integer> f3 = Flux.range(7, 3);
//
//        Flux.concat(
//                f1.delayElements(Duration.ofSeconds(2)).doOnComplete(() -> System.out.println("completed!")),
//                f2.delayElements(Duration.ofSeconds(2)).doOnComplete(() -> System.out.println("completed!")),
//                f3.delayElements(Duration.ofSeconds(2)).doOnComplete(() -> System.out.println("completed!"))
//        ).log().subscribe(System.out::println);

//        Flux.merge(
//                f1.delayElements(Duration.ofSeconds(2)).doOnComplete(() -> System.out.println("completed!")),
//                f2.delayElements(Duration.ofSeconds(2)).doOnComplete(() -> System.out.println("completed!")),
//                f3.delayElements(Duration.ofSeconds(2)).doOnComplete(() -> System.out.println("completed!"))
//        ).log().subscribe(System.out::println);

//        f1.concatWith(f2.concatWith(f3)).log().subscribe();

//        Mono<String> mono1 = Mono.fromCallable(() -> {
//            Thread.sleep(5000L);
//            return "data";
//        });

//        mono1.log().subscribe();
//        mono1.log().subscribe();
//        mono1.log().subscribeOn(Schedulers.boundedElastic()).subscribe();
//        mono1.log().subscribeOn(Schedulers.boundedElastic()).subscribe();
//        mono1.log().subscribeOn(Schedulers.boundedElastic()).filter(d -> true).publishOn(Schedulers.boundedElastic()).map(d -> "map").subscribe();
//        mono1.log().filter(d -> true).publishOn(Schedulers.boundedElastic()).map(d -> "map").subscribe();
//        Flux.just(1,2,3).subscribe(System.out::println);
//        mono1.subscribeOn(Schedulers.boundedElastic()).publishOn(Schedulers.boundedElastic()).log().subscribe();
//        mono1.publishOn(Schedulers.boundedElastic()).log().subscribe();

//        Flux.range(1, 3)
//                .publishOn(Schedulers.boundedElastic())
//                        .flatMap(e -> Mono.fromCallable(() -> {
//                            Thread.sleep(5000L);
//                            return e * 2;
//                        })).log().subscribeOn(Schedulers.boundedElastic()).subscribe(System.out::println);

        /*Flux.range(1, 3)
//                .publishOn(Schedulers.boundedElastic())
                        .flatMap(e -> {
                            try {
                                Thread.sleep(5000L);
                            } catch(Exception err) {
                                err.printStackTrace();
                            }
                            return Mono.just(e * 2);
                        }).log().subscribe(System.out::println);*/

//        Flux.range(1, 3).publishOn(Schedulers.boundedElastic()).map(e -> {
//            try {
//                Thread.sleep(5000L);
//            } catch(Exception err) {
//                err.printStackTrace();
//            }
//            return e * 2;
//        }).log().subscribe();
//        Flux.just("one", "two").log().subscribeOn(Schedulers.boundedElastic()).subscribe(System.out::println);
//        Flux.just("one", "two").log().subscribe(System.out::println);
//        Flux.just("one", "two").map(e -> e + "-map").log().subscribe(System.out::println);
//        Flux.just("one", "two").log().subscribeOn(Schedulers.single()).map(e -> e + "-map").log().subscribeOn(Schedulers.parallel()).subscribe(System.out::println);
//        Flux.just("one", "two").log().subscribeOn(Schedulers.boundedElastic()).map(e -> e + "-map").log().subscribeOn(Schedulers.boundedElastic()).subscribe(System.out::println);

        // just도 subscribe가 있고, 여기서 actual.onSubscribe(s)로 전달.

        // subscriberContext is deprecated
        // deferContextual
        // transformDeferContextual
//        String key = "message";
//        Mono<String> r = Mono.just("Hello")
//                        .flatMap(s -> Mono.deferContextual(ctx ->
//                                Mono.just(s + " " + ctx.get(key))))
//                                .contextWrite(ctx -> ctx.put(key, "World"));
//
//        r.subscribe(System.out::println);
//        String num = "num";
//        Flux.range(1, 10).transformDeferredContextual((flux, ctx) -> flux.take(ctx.get(num))).contextWrite(ctx -> ctx.put(num, 3)).log().subscribe(System.out::println);
//        Flux.just("one", "two").transformDeferredContextual((original, ctx) -> original.map(e -> e + " " + ctx.get(num))).contextWrite(ctx -> ctx.put(num, 3)).subscribe(System.out::println);

//        Flux<String> stream = Flux.just("one", "two", "three")
//                        .publishOn(Schedulers.boundedElastic())
//                .contextWrite(ctx -> ctx.put(key, "World1"))
//                                        .publishOn(Schedulers.parallel())
//                                                .flatMap(s -> Mono.deferContextual(ctx ->
//                                                        Mono.just(s + " " + ctx.get(key))));
//
//
//        stream.contextWrite(ctx -> ctx.put(key, "World")).subscribe(System.out::println);

//        Flux.interval(Duration.ofMillis(100)).log().limitRate(10).subscribe(System.out::println);

//        Flux<Integer> stream = Flux.just(1, 2, 3).delayElements(Duration.ofSeconds(2));
//        stream.subscribe(s -> System.out.println("first" + s));
//        Thread.sleep(2000L);
//        stream.subscribe(s -> System.out.println("second" + s));

//
        Thread.sleep(30000L);
    }

    static void createStream() {
        Flux<String> stream1 = Flux.just("one", "two", "three");
        Flux<Long> stream2 = Flux.interval(Duration.ofSeconds(1));
        Flux<Integer> stream3 = Flux.range(1, 5);
        Flux<String> stream4 = Flux.fromArray(new String[]{"one", "two", "three"});
        Flux<String> stream5 = Flux.fromIterable(Arrays.asList("one", "two", "three"));

        // single-threaded
        Flux<String> stream6 = Flux.push(emitter -> {
            emitter.next("one");
            emitter.next("two");
            emitter.next("three");
            emitter.complete();
        });

        // multi-threaded
        Flux<String> stream7 = Flux.create(emitter -> {
            emitter.next("one");
            emitter.next("two");
            emitter.next("three");
            emitter.complete();
        });

        Flux<Integer> stream8 = Flux.generate(
                () -> 10,
                (state, emitter) -> {
                    int current = state + 1;
                    emitter.next(current);
                    if (current == 15) emitter.complete();
                    return current;
                },
                (state) -> System.out.println("clean up :" + state));

        stream8.log().subscribe(System.out::println);
    }

    static void subscribing() {
        Flux<String> stream1 = Flux.just("one", "two", "three");

        stream1.subscribe(e -> System.out.println(e));
        stream1.subscribe(
                e -> System.out.println(e),
                err -> err.printStackTrace(),
                () -> System.out.println("completed"));
        //Deprecated
        stream1.subscribe(
                e -> System.out.println(e),
                err -> err.printStackTrace(),
                () -> System.out.println("completed"),
                s -> s.request(4));

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
        }

        CoreSubscriber<String> subscriber = new CustomSubscriber<>();
        stream1.subscribe(subscriber);
    }

    static void emptyOfNever() {
//        Mono<String> mono1 = Mono.just(null);

        Mono<Void> mono2 = Mono.empty();
        Mono<String> mono3 = Mono.justOrEmpty(null);
        Mono<String> mono4 = Mono.justOrEmpty(Optional.empty());

        mono4.log().subscribe();

        Mono<Void> mono5 = Mono.never();

        mono5.log().subscribe();

        Flux<Void> stream1 = Flux.empty();
        Flux<Void> stream2 = Flux.never();
        stream2.log().subscribe();

        Mono.error(new RuntimeException("something wrong happened")).onErrorResume(e -> Mono.empty()).subscribe();
    }

    static Mono<String> requestUser(String input) {
        return isValidSession(input)
                ? Mono.fromCallable(() -> "user")
                : Mono.error(new RuntimeException("invalid user"));
    }

    static Boolean isValidSession(String sessionId) {
        Random rd = new Random();
        return rd.nextBoolean();
    }

    static Integer[] makeRandomArray() {
        Random rd = new Random();
        int max = rd.nextInt(10);
        Integer[] result = new Integer[max];
        for (int i = 0; i < max; i++) {
            result[i] = i;
        }
        return result;
    }
    static void defer() {
        Mono<String> eagerMono = requestUser("user");

        class ErrorLogSubscriber<T> extends BaseSubscriber<T> {
            @Override
            protected void hookOnError(Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        CoreSubscriber<String> errorLogSubscriber = new ErrorLogSubscriber<>();

//        eagerMono.subscribe(errorLogSubscriber);
//        eagerMono.subscribe(errorLogSubscriber);
//        eagerMono.subscribe(errorLogSubscriber);
//        eagerMono.subscribe(errorLogSubscriber);

        Mono<String> lazyMono = Mono.defer(() -> requestUser("user"));

//        lazyMono.subscribe(errorLogSubscriber);
//        lazyMono.subscribe(errorLogSubscriber);
//        lazyMono.subscribe(errorLogSubscriber);
//        lazyMono.subscribe(errorLogSubscriber);

        Integer[] randomIntArray = makeRandomArray();
//        Flux.fromArray(randomIntArray).log().subscribe(System.out::println);
//        Flux.fromArray(randomIntArray).log().subscribe(System.out::println);
//        Flux.fromArray(randomIntArray).log().subscribe(System.out::println);
//        Flux.defer(() -> Flux.fromArray(makeRandomArray())).subscribe(e -> System.out.println(e));
//        Flux.defer(() -> Flux.fromArray(makeRandomArray())).subscribe(e -> System.out.println(e));
//        Flux.defer(() -> Flux.fromArray(makeRandomArray())).subscribe(e -> System.out.println(e));
    }

    static void operator() {
//        // timestamp
//        Flux.range(100, 5)
//                .timestamp()
//                .subscribe(e -> {
//                    System.out.println(Instant.ofEpochMilli(e.getT1()));
//                    System.out.println(e.getT2());
//                });
//
//        // index
//        Flux.range(100, 5)
//                .index()
//                .subscribe(e -> {
//                    System.out.println(e.getT1());
//                    System.out.println(e.getT2());
//                });
//
//        // ignoreElements
//        Flux.just("one", "two", "three")
//                .ignoreElements()
//                .log()
//                .subscribe(System.out::println);
//
//        // take
//        // takeLast
//
//        // takeUntil
//        Predicate<String> isClosed = e -> e == "close";
//        Flux.just("open", "open", "close", "open", "open")
//                .takeUntil(isClosed)
//                .subscribe(System.out::println);
//
//        // elementAt
//        Flux.just("open", "open", "close", "open", "open")
//                .elementAt(1)
//                .subscribe(System.out::println);
//
//        // single
//        Flux.empty()
//                .single()
//                .subscribe(System.out::println);
//        Flux.just("one")
//                .single()
//                .subscribe(System.out::println);
//        Flux.just("one", "two")
//                .single()
//                .subscribe(System.out::println);
//
//        // skip
//        Flux.interval(Duration.ofSeconds(1))
//                .skip(Duration.ofSeconds(5))
//                .subscribe();
//
//        Flux.interval(Duration.ofSeconds(1))
//                .skip(5)
//                .subscribe();
//
//        // take
//
//        // skipUntilOther
//        // takeUntilOther
//        Flux.interval(Duration.ofSeconds(1))
//                .skipUntilOther(Mono.just("start").delayElement(Duration.ofSeconds(3)))
//                .takeUntilOther(Mono.just("stop").delayElement(Duration.ofSeconds(10)))
//                .subscribe(System.out::println);
//
//        // repeat
//        Flux.just("repeat one", "repeat two")
//                .repeat(3)
//                .subscribe(System.out::println);
//
//        // defaultIfEmpty
//        Flux.empty()
//                .defaultIfEmpty("default")
//                .subscribe(System.out::println);
//
//        // distinct
//        // distinctUntilChanged
//        Flux<String> example = Flux.just("hello", "hello", "hello", "bye", "bye", "hello", "hello");
//        example
//                .distinct()
//                .subscribe(System.out::println);
//        example
//                .distinctUntilChanged()
//                .subscribe(System.out::println);
//
//        Flux.just("a", "b")
//                .collectSortedList(Comparator.reverseOrder())
//                .subscribe(System.out::println);
//
//        // count
//
//        // all(Predicate)
//        Flux.just("fruit", "vegetable", "fruit")
//                .all(e -> e == "fruit")
//                .subscribe(System.out::println);
//
//        // any(Predicate)
//        Flux.just("fruit", "vegetable", "fruit")
//                .any(e -> e == "fruit")
//                .subscribe(System.out::println);
//
//        // hasElements
//        Flux.just("fruit", "vegetable", "fruit")
//                .hasElement("fastfood")
//                .subscribe(System.out::println);
//
//        // hasElement
//        Flux.empty().hasElements().subscribe(System.out::println);
//        Flux.just("hello").hasElements().subscribe(System.out::println);
//
//        // sort
//
        // reduce
//        Flux.just("a", "b", "c")
//            .reduce((result, e) -> result + e)
//            .subscribe(System.out::println);
//
//        // scan
//        Flux.just("a", "b", "c")
//            .scan((result, e) -> result + e)
//            .subscribe(System.out::println);
//
//        // then
//        Flux.just("something", "more")
//                .then(Mono.just("else"))
//                .subscribe(System.out::println);
//
//        Flux.just("something", "more")
//                .then(Mono.empty())
//                .subscribe(System.out::println);
//
//        // thenMany
//        Flux.just("something", "more")
//                .thenMany(Flux.just("else", "else2"))
//                .subscribe(System.out::println);
//
//        // thenEmpty
//        Flux.just("something", "more")
//                .thenEmpty(Flux.empty())
//                .subscribe(System.out::println);
    }

    static void combining() {
        Flux.concat(
                Flux.range(1,3).delayElements(Duration.ofMillis(1500)).doOnComplete(() -> System.out.println("completed !")),
                Flux.range(4, 2).delayElements(Duration.ofMillis(1500)).doOnComplete(() -> System.out.println("completed !")),
                Flux.range(6, 5).delayElements(Duration.ofMillis(1500)).doOnComplete(() -> System.out.println("completed !"))
        ).subscribe(e -> System.out.println(e));

        Flux.merge(
                Flux.range(1, 3).delayElements(Duration.ofMillis(1500)).doOnComplete(() -> System.out.println("completed !")),
                Flux.range(4, 2).delayElements(Duration.ofMillis(1500)).doOnComplete(() -> System.out.println("completed !")),
                Flux.range(6, 5).delayElements(Duration.ofMillis(1500)).doOnComplete(() -> System.out.println("completed !"))
        ).subscribe(e -> System.out.println(e));;

        Flux<String> f1 = Flux.just("f1", "g1");
        Flux<String> f2 = Flux.just("f2", "g2", "h2");
        Flux<String> f3 = Flux.just("f3", "g3", "h3", "i3");

        Flux.zip(f1, f2, f3).delayElements(Duration.ofSeconds(2)).subscribe(
                e -> System.out.println(e),
                error -> error.printStackTrace(),
                () -> System.out.println("complted!")
        );
        Flux.combineLatest(f1, f2, (a, b) -> a + b).subscribe(e -> System.out.println(e));
        Flux.combineLatest(f1, f2, f3, array -> Arrays.toString(array)).subscribe(e -> System.out.println(e));
    }

    static void batching() {
//        // buffer -> Flux<List<T>>
//        Flux.range(1,14)
//                .buffer(5)
//                .doOnComplete(() -> System.out.println("completed"))
//                .subscribe(System.out::println);
//
//        // widnow -> Flux<Flux<T>>
//        Flux.range(1, 14)
//                .window(5)
//                .subscribe(window -> window.doOnComplete(() -> System.out.println("completed")).subscribe(System.out::println));
//
//        Flux.interval(Duration.ofMillis(500))
//                .window(Duration.ofSeconds(2))
//                .subscribe(window -> window.doOnComplete(() -> System.out.println("completed")).subscribe(System.out::println));

//        Flux.range(1, 13)
//                .groupBy(e -> e % 2 == 0 ? "Even" : "Odd")
//                .subscribe(groupFlux -> groupFlux.collectList().subscribe(System.out::println));
//
//        Flux.range(1, 100)
//                .delayElements(Duration.ofMillis(1))
//                .sample(Duration.ofMillis(20))
//                .subscribe(e -> System.out.println(e));
    }

    static void flatMapEx() {
        Function<String, Flux<String>> requestBooks = user -> Flux.range(1, new Random().nextInt(3) + 1).map(i -> user + "/book-" + i).delayElements(Duration.ofMillis(10));

        Flux.just("user-1", "user-2", "user-3")
                .flatMap(requestBooks)
                .log()
                .subscribe(System.out::println);

        Flux.just("user-1", "user-2", "user-3")
                .flatMapSequential(requestBooks)
                .log()
                .subscribe(System.out::println);
    }

    static void errorHandling() {
        Flux<Integer> error = Flux.error(new RuntimeException("something happend!"));

        error.onErrorResume(err -> Flux.just(1,2,3)).subscribe(System.out::println);

        Flux.just(1,2,3).map(e -> {
            if (e == 2) throw new RuntimeException("error");
            return e;
        }).subscribe(System.out::println);

        Flux.just(1,2,3).map(e -> {
            if (e == 2) throw new RuntimeException("error");
            return e;
        }).onErrorContinue((err, e) -> System.out.println(e)).subscribe(System.out::println);

        error.onErrorMap(e -> new RuntimeException("changed")).subscribe(System.out::println, err -> System.out.println(err));

        error.log().retry(3).onErrorResume(err -> Mono.empty()).subscribe();
        error.log().retryWhen(Retry.backoff(3, Duration.ofSeconds(2))).onErrorResume(err -> Mono.empty()).subscribe();
    }

    static void hotStream() throws InterruptedException {
        Flux<Integer> source = Flux.range(0, 30).delayElements(Duration.ofMillis(500L)).doOnSubscribe(s -> System.out.println("new subscription"));
        ConnectableFlux<Integer> conn = source.publish();

        conn.buffer(10).subscribe(e -> System.out.println("sub1: " + e));
        conn.connect();
        Thread.sleep(10000L);
        conn.subscribe(e -> System.out.println("sub2: " + e));
    }

    static void hotStream2() throws InterruptedException {
        Flux<Integer> stream = Flux.range(1, 10).delayElements(Duration.ofSeconds(2)).share();
        stream.subscribe(System.out::println);
        Thread.sleep(5000L);
        stream.subscribe(System.out::println);
    }

    static void backpressureStrategy() {
//        Flux.interval(Duration.ofMillis(1))
//                .log()
//                .onBackpressureDrop(e -> System.out.println("dropped: " + e))
//                .log()
//                .concatMap(a -> Mono.delay(Duration.ofMillis(10)).thenReturn(a))
//                .doOnNext(a -> System.out.println("Element kept by consumer: " + a))
//                .blockLast();

        Flux.interval(Duration.ofMillis(1))
//        Flux.range(1, 200)
                .log()
                .limitRate(100)
//                .log()
                .concatMap(a -> Mono.delay(Duration.ofMillis(10)).thenReturn(a))
                .doOnNext(a -> System.out.println("Element kept by consumer: " + a))
                .subscribe(System.out::println);
    }

    static void reactorContext() {
        String key = "message";
        Mono<String> r = Mono.just("Hello")
                        .flatMap(s -> Mono.deferContextual(ctx ->
                                Mono.just(s + " " + ctx.get(key))))
                                .contextWrite(ctx -> ctx.put(key, "World"));

//        r.subscribe(System.out::println);
//
//        String num = "num";
//        Flux.just("one", "two").transformDeferredContextual((original, ctx) -> original.map(e -> e + " " + ctx.get(num))).contextWrite(ctx -> ctx.put(num, 3)).subscribe(System.out::println);

        Flux<String> stream = Flux.just("one", "two", "three")
                                    .publishOn(Schedulers.boundedElastic())
                                    .flatMap(s -> Mono.deferContextual(ctx ->
                                        Mono.just(s + " " + ctx.get(key))))
                                    .contextWrite(ctx -> ctx.put(key, "World1"))
                                    .publishOn(Schedulers.parallel())
                                    .flatMap(s -> Mono.deferContextual(ctx ->
                                        Mono.just(s + " " + ctx.get(key))));


        stream.contextWrite(ctx -> ctx.put(key, "World")).subscribe(System.out::println);
    }
}
