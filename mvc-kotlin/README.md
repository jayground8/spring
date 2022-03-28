- [Kotlin Coroutine in Spring](#kotlin-coroutine-in-spring)
  - [Kotlin Coroutines Proposal](#kotlin-coroutines-proposal)
  - [Asynchronous Programming with Flux and Kotlin](#asynchronous-programming-with-flux-and-kotlin)
  - [Kotlin in Spring Web MVC](#kotlin-in-spring-web-mvc)
  - [Imperative Programming with Kotlin Coroutine](#imperative-programming-with-kotlin-coroutine)
- [결론](#결론)
- [참고자료](#참고자료)

# Kotlin Coroutine in Spring

Python에서 AsyncIO와 Coroutine를 통해서 Non-Blocking & Asynchronous 프로그래밍을 했었다. Python에서는 GIL때문에 single thread에서 작동되기 때문에 thread를 생각할 필요가 없었다. Golang에서는 Goroutine을 사용하여 동시성을 확보하였는데, Goroutine은 mutli-core을 활용하기 위해서 여러 thread에서 Goroutine이 실행될 수 있다. 그리고 CSP(Communicating Sequential Processes)<sup>[1][1]</sup>의 구현체인 Channel을 통해서 locking 없이 thread safe하게 작동할 수 있다.

🤔 **Kotlin의 Coroutine은 어떠한 특성을 가지고 있는지 궁금해졌고, Kotlin Coroutine을 Spring에서 어떻게 활용해야지 효율적인 것인지 궁금해졌다.**

## Kotlin Coroutines Proposal

Kotlin Coroutines Proposal<sup>[2][2]</sup>에서 Coroutine이 어떻게 작동할 수 있는지 친절히 설명하고 있다. 인상적인 부분은 아래와 같은 설명이었다.

> Make it possible to utilize Kotlin coroutines as wrappers for different existing asynchronous APIs (such as Java NIO, different implementations of Futures, etc).

Proposal goal에 다른 asynchronous API도 wrapping하는 것이 있었다. 따라서 CompletableFuture도 module을 설치해서 Coroutine으로 감쌀 수 있는 것이었다.

Kotlinx.coroutines module 리스트<sup>[3][3]</sup>에는 CompletableFuture뿐만 아니라 Java 9의 Flow, rxJava등을 위한 모듈들도 존재한다.

예를 들어 CompletableFuture로 사용해서는 아래처럼 작성할 수 있다. CompletableFuture의 async 함수는 따로 Executor를 설정해주지 않으면 ForkJoinPool의 별도 thread에서 실행되게 된다. 따라서 `ForkJoinPool.commonPool-worker` thread에서 실행될 것을 확인할 수 있다.

```kotlin
fun getData(name: String): CompletableFuture<String> {
    return CompletableFuture.supplyAsync {
        sleep(10000)
        name
    }
}

fun main() = runBlocking {
    val a = async { getData("Durian").await() }
    val b = async { getData("Coco").await() }
    val c = async { getData("Jerry").await() }
    val result = listOf<String>(a.await(), b.await(), c.await())
    println(result)
}
```

KotlinConf 2017에서 발표한 Deep Dive into Coroutines on JVM<sup>[4][4]</sup>에서 어떻게 Coroutine이 작동될 수 있는지 잘 설명하고 있다.

Kotlin Coroutine에서는 중단될 수 있는 suspension point를 LABEL이 등록된 부분으로 잡게 된다. 

```kotlin
suspend fun postItem(item: Item) {
    // LABEL 0
    val token = requestToken()
    // LABEL 1
    val post = createPost(token, item)
    // LABEL 2
    processPost(post)
}
```

그리고 Coroutine이 suspenstion point에서 중단되고 나중에 다시 실행되면 중단된 시점부터 다시 시작되어야 한다. 그래서 아랫처럼 `sm`과 같은 변수에 다음에 실행된 label값을 가짐으로써 이어서 다음 스텝을 실행할 수 있다. 실제로 IntelliJ에서 `Kotlin Bytecode show`를 통해서 보면 아랫처럼 `Switch문`과 `label`이 있는 것을 확인 할 수 있다.

```kotlin
fun postItem(item: Item, cont: Continuation) {
    val sm = cont as? ThisSM ?: object : ThisSM {
        fun resume(...) {
            postItem(null, this)
        }
    }
  
    switch (sm.label) {
        case 0:
            sm.item = item
            sm.label = 1
            requestToken(sm)
        case 1:
            val item = sm.item
            val token = sm.result as Token
            sm.label = 2
            createPost(token, item, sm)
        case 2:
            ...
    }
}
```

## Asynchronous Programming with Flux and Kotlin

`Webflux`의 `WebClient`와 Flux의 `flatMap`을 통해서 비동기적으로 request를 아랫처럼 할 수 있다. `WebClient`는 `reactor-netty`을 통해서 Non-blocking으로 작동한다.

```java
public class Example {
    private static WebClient client = WebClient.create("http://localhost:8080");

    public static void main(String[] args) {
        List<String> messages = Flux.range(1,3)
                .flatMap(i ->
                        client.get().uri("/")
                                .retrieve()
                                .bodyToMono(String.class)
                ).log().collectList().block();

        System.out.println(messages);
    }
}
```

`log()`를 통해서 이렇게 간단하게 `console`에서 어떻게 실행되었는지 확인할 수 있다.

```log
[main] INFO reactor.Flux.FlatMap.1 - onSubscribe(FluxFlatMap.FlatMapMain)
[main] INFO reactor.Flux.FlatMap.1 - request(unbounded)
[reactor-http-nio-3] INFO reactor.Flux.FlatMap.1 - onNext(hello world!)
[reactor-http-nio-3] INFO reactor.Flux.FlatMap.1 - onNext(hello world!)
[reactor-http-nio-3] INFO reactor.Flux.FlatMap.1 - onNext(hello world!)
[reactor-http-nio-3] INFO reactor.Flux.FlatMap.1 - onComplete()
```

이번에는 Kotlin의 Coroutines을 사용해서 작성해보면 아랫처럼 작성할 수 있다. WebClient가 `reactor-netty` 위에서 `reactive stream`으로 작동한다. 이제 Kotlinx.coroutines module 리스트<sup>[3][3]</sup>에서 `kotlinx-coroutines-reactor`를 통해서 coroutine으로 감싸서 사용할 수 있게 된다. Kotlin에서 `awaitBody()`를 통해서 `Deffered`가 된다.<sup>[5][5]</sup> Webclient를 사용할 때 Kotlin에서 사용할 수 있는 function들이 여기<sup>[6][6]</sup>에 나와 있다. `awaitBody()`는 Spring 5.2부터 사용할 수 있는 것으로 나온다.

```kotlin
val client = WebClient.create("http://localhost:8080")

fun main() = runBlocking() {
    val a = async {
        client.get().uri("/?name=Durian").retrieve().awaitBody<String>()
    }

    val b = async {
        client.get().uri("/?name=Coco").retrieve().awaitBody<String>()
    }

    val c = async {
        client.get().uri("/?name=Jerry").retrieve().awaitBody<String>()
    }

    val messages = listOf<String>(a.await(), b.await(), c.await())
    println(messages)
}
```

`WebClient` 대신에 `Ktor`의 `Client`를 사용하여서 아랫처럼 작성할 수도 있다. `Ktor Client`는 network request를 처리하는 `Engine`<sup>[7][7]</sup>을 선택할 수 있다. 아래의 예제에서는 Engine을 `CIO`를 사용하였고, 따라서 `io.ktor:ktor-client-cio:1.6.8` dependency가 추가되었다. CIO는 coroutine으로 감싸기 위한 별도의 모듈 필요없이 Coroutine base로 I/O 로직이 동작하는 것을 의미한다.

> CIO stands for Coroutine-based I/O. Usually we call it to an engine that uses Kotlin and Coroutines to implement the logic implementing an IETF RFC or another protocol without relying on external JVM-based libraries.

```kotlin
suspend fun getResult(client: HttpClient, name: String): String {
    val response: HttpResponse = client.get("http://localhost:8080/?name=${name}")
    return response.receive()
}

fun main() = runBlocking(Dispatchers.Unconfined) {
    val client = HttpClient(CIO)
    val a = async { getResult(client, "Durian")}
    val b = async { getResult(client, "Coco")}
    val c = async { getResult(client, "Jerry")}
    val result = listOf(a.await(), b.await(), c.await())
    println(result)
    client.close()
}
```

## Kotlin in Spring Web MVC

Spring에서 어떻게 Coroutine을 지원하는지는 이 문서<sup>[8][8]</sup>을 통해서 확인할 수 있다.

`fun handler(): Mono<T>`로 작성한 코드는 `suspend fun handler(): T`로 해석되고, `fun hanlder(): Flux<T>`로 작성한 코드는 `fun handler(): Flow<T>`로 해석된다. 따라서 handler를 아랫처럼 작성할 수 있다.

```kotlin
@GetMapping("/kotlin/cats")
fun kgetCats() : Flux<Cat> {
    return catRepository.findAll()
}

@GetMapping("/kotlin/cats/{id}")
fun kgetCatById(@PathVariable id: Int): Mono<Cat> {
    return catRepository.findById(id)
}
```

[Spring MVC에서 R2DBC를 사용하면 어떠한 장점이 있을까?](https://github.com/jayground8/spring/tree/main/r2dbc) 에서 `Spring Web MVC`의 Controller에서도 `Flux`나 `Mono`를 리턴할 수 있다는 것을 알았다. `Spring Web MVC`에서 `Mono`와 `Flux`를 타입을 아래와 같이 변환하여 Servlet 3.0의 비동기 기능을 사용한다.

|Reactive Type| Spring MVC에서 변환 | 
|---|---|
|Mono| DefferedResult|
|Flux / non-streaming| DefferedResult<Lit<T>>|
|Flux / streaming|ResponseBodyEmitter with backpressure|

이제 handler의 fun을 `suspend fun`으로 작성하게 되면 이것은 비동기적으로 작동하게 된다.

```kotlin
@GetMapping("/coroutine/async/cats")
suspend fun kgetCatById(): List<Cat?> = coroutineScope {
    val cat1 = async {
        delay(10000)
        catRepositoryCoroutine.getCatById(983)
    }
    val cat2 = async {
        delay(10000)
        catRepositoryCoroutine.getCatById(984)
    }

    listOf(cat1.await(), cat2.await())
}
```

이제 `runBlocking`을 사용하면 동기적으로 handler를 처리할 수 있다. handler를 처리하는 thread에서 coroutine을 통해서 동시성을 확보하게 된다.

```kotlin
@GetMapping("/coroutine/sync/cats")
fun getBlock(): List<Cat?> = runBlocking() {
    val cat1 = async(CoroutineName("cat1")) {
        delay(10000)
        catRepository.findById(983).awaitSingle()
    }
    val cat2 = async(CoroutineName("cat2")) {
        delay(10000)
        catRepository.findById(984).awaitSingle()
    }
    listOf(cat1.await(), cat2.await())
}
```

Reactor Project에서 `subscribeOn`, `publishOn` 을 통해서 별도의 thread에서 operator나 subcriber을 실행할 수 있었다. Blocking 코드가 있으면 Schedulers.elastic()을 통해서 매번 다른 thread에서 실행될 수 있도록 할 수 있다. Kotlin Coroutine에서도 `Dispatchers`가 존재하고 `Dispatchers.IO`를 통해서 blocking되는 I/O 작업일 경우 새로운 thread를 만들어서 Coroutine이 실행될 수 있도록 할 수 있다. Coroutine은 어느 thread에서도 작동할 수 있다라는 것을 기억해야 한다.

그리고 위에서 handler를 `suspend fun`으로 작성할 때는 `Dispachers.Unconfined`로 기본으로 설정된다.

> Dispatchers.Unconfined — starts coroutine execution in the current call-frame until the first suspension, whereupon the coroutine builder function returns. The coroutine will later resume in whatever thread used by the corresponding suspending function, without confining it to any specific thread or pool. The Unconfined dispatcher should not normally be used in code.<sup>[9][9]</sup>

따라서 아래처럼 thread의 이름을 console에 찍도록 하면, 처음은 Spring web MVC의 worker thread인 `http-nio-8080-exec`가 찍히고, 그다음에는 `kotlinx.coroutines.DefaultExecutor`에서 실행된다. 

```
@GetMapping("/coroutine/unconfined")
suspend fun getNonBlock(): String {
    println("a ${Thread.currentThread().name}")
    delay(3000)
    println("b ${Thread.currentThread().name}")
    return "unconfined"
}
```

## Imperative Programming with Kotlin Coroutine

`reactive programming` vs `imperative programming` 

당근마켓에서 진행한 밋업 "Kotlin Coroutines 톺아보기"<sup>[10][10]</sup>에서는 아래처럼 reactive stream으로 데이터베이스에서 어떤 데이터를 쿼리하고 그걸 바탕으로 계속 다른 쿼리를 해야 되는 경우 어떻게 Kotlin Coroutine이 더 읽기 쉬운 코드를 만들어주는지 설명하고 있다. 

```kotlin
fun execute(inputValues: InputValues): Mono<Order> {
    val (userId, productIds) = inputValues

    return Mono.create { emitter ->
        userRepository.findUserByIdAsMaybe(userId)
            .subscribe { buyer ->
                addressRepository.findAddressByUserAsPublisher(buyer)
                    .subscribe(LastItemSubscriber { address ->
                        checkValidRegion(address)
                        productRepository.findAllProductsByIdsAsFlux(productIds)
                            .collectList()
                            .subscribe { products ->
                                check(products.isNotEmpty())
                                storeRepository.findStoresByProductsAsMulti(products)
                                    .collect().asList()
                                    .subscribe().with { stores ->
                                        check(stores.isNotEmpty())
                                        orderRepository.createOrderAsFuture(
                                            buyer, products, stores, address
                                        ).whenComplete { order, _ ->
                                            emitter.success(order)
                                        }
                                    }
                            }
                    })
            }
    }
}
```

```kotlin
suspend fun execute(inputValues: InputValues): Order {
    val (userId, productIds) = inputValues

    // 1. 구매자 조회
    val buyer = userRepository.findUserByIdAsMaybe(userId).awaitSingle()

    // 2. 주소 조회 및 유효성 체크
    val address = addressRepository.findAddressByUserAsPublisher(buyer)
        .awaitLast()
    checkValidRegion(address)

    // 3. 상품들 조회
    val products = productRepository.findAllProductsByIdsAsFlux(productIds).asFlow().toList()
    check(products.isNotEmpty())

    // 4. 스토어 조회
    val stores = storeRepository.findStoresByProductsAsMulti(products).asFlow().toList()
    check(stores.isNotEmpty())

    // 5. 주문 생성
    val order = orderRepository.createOrderAsFuture(buyer, products, stores, address).await()

    return order
}
```

# 결론

Kotlin Coroutine은 별도의 thread를 생성하지 않고 동시성을 가져올 수 있다. Kotlin Coroutine은 어느 thread에서도 실행될 수 있다. 그래서 compute-intensive한 작업은 Core갯수만큼 만들어지는 thread pool인 Dispatchers.Default에서 실행하고, blocking I/O 작업이 있는 경우에는 Dispatchers.IO으로 새로운 thread에서 Coroutine이 실행되도록 할 수 있다. 

Kotlin Coroutine은 Java에서 비동기 프로그래밍을 할 수 있는 CompletableFuture, Java 9의 Flow, reactive stream(RxJava, Reactor 등)을 Coroutine으로 감싸서 사용할 수 있도록 다양한 모듈을 제공한다. Reactive Stream의 declarative(reactive) programming으로 작성한 것을 Koltin Coroutine으로 Imperative하게 작성할 수도 있다. 데이터베이스에서 쿼리한 결과값을 가지고 순차적으로 계속 데이터베이스에 쿼리를 하는 상황에서는 Kotlin Coroutine으로 imperative하게 작성하는 것이 더 깔끔해보인다. 하지만 Consumer가 request하는 만큼만 처리고, data가 stream으로 흘러가면서 다양한 데이터 변환이 필요하다고 하면 reactive programming이 읽기 더 편리할 것 같다.

Spring Web MVC에서는 이제 Servlet 3.0 기능을 이용하여 controller handler를 비동기적으로 처리할 수 있고, Mono와 Flux를 리턴하면 내부적으로 DefferedResult나 ResponseBodyEmitter로 변환되어 처리 된다. suspend fun으로 handler를 작성하면 이렇게 비동기적으로 처리가 된다. 그리고 별도의 Dispatcher를 설정하지 않으면 `Unconfined`로 설정이 되어 첫번째 suspension point 이후에는 `DefaultExecutor` thread에서 실행이 되게 된다. Kotlin 문서에는 `The Unconfined dispatcher should not normally be used in code`라고 안내가 되어 있는데, 이렇게 `Unconfined dispatcher`를 사용해도 되는 것인지 더 고민해봐야겠다.

🙃 처음 Kotlin Coroutine을 들었을 때는 성능적으로 뭔가 이득이 있을까 궁금증이 생겼다. 하지만 이미 Java에서 Non-blocking & Asynchronous programming을 할 도구가 많고, 단순한 multi-threading과 비교하는 것이 아니라면 이러한 여러 도구와 성능적으로 크게 차이날 이유는 없다. 따라서 Kotlin Coroutine은 Java에서 사용할 수 있는 다양한 비동기 프로그래밍 방법 이외 또 다른 한가지 방법으로 생각할 수 있을 것 같다. 🤔 그러면 Kotlin Coroutine을 쓰는 것은 Kotlin의 언어가 가져오는 장점들을 이용하는 것이 가장 큰 이유지 않을까? 아직 나는 Kotlin에서 제공하는 언어적 간결함과 Null safety의 유용함을 피부로 느껴보지 못했기 때문에, Kotlin + Coroutine이 더 매력적으로 느껴지진 않는다. 앞으로 Kotlin과 친해지면 이러한 생각이 바뀌게 될지 궁금하다. 😏

[1]: https://en.m.wikipedia.org/wiki/Communicating_sequential_processes

[2]: https://github.com/Kotlin/KEEP/blob/master/proposals/coroutines.md

[3]: https://kotlin.github.io/kotlinx.coroutines/

[4]: https://youtu.be/YrrUCSi72E8

[5]: https://spring.getdocs.org/en-US/spring-framework-docs/docs/spring-web-reactive/webflux-client/webflux-client-body.html

[6]: https://docs.spring.io/spring-framework/docs/5.3.5/kdoc-api/spring-framework/org.springframework.web.reactive.function.client/index.html

[7]: https://ktor.io/docs/http-client-engines.html

[8]: https://spring.getdocs.org/en-US/spring-framework-docs/docs/languages/kotlin/coroutines.html

[9]: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/

[10]: https://www.youtube.com/watch?v=eJF60hcz3EU&t=154s

# 참고자료

1: https://en.m.wikipedia.org/wiki/Communicating_sequential_processes

2: https://github.com/Kotlin/KEEP/blob/master/proposals/coroutines.md

3: https://kotlin.github.io/kotlinx.coroutines/

4: https://youtu.be/YrrUCSi72E8

5: https://spring.getdocs.org/en-US/spring-framework-docs/docs/spring-web-reactive/webflux-client/webflux-client-body.html

6: https://docs.spring.io/spring-framework/docs/5.3.5/kdoc-api/spring-framework/org.springframework.web.reactive.function.client/index.html

7: https://ktor.io/docs/http-client-engines.html

8: https://spring.getdocs.org/en-US/spring-framework-docs/docs/languages/kotlin/coroutines.html

9: https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/-coroutine-dispatcher/

10: https://www.youtube.com/watch?v=eJF60hcz3EU&t=154s