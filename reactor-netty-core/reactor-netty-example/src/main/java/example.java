import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.tcp.TcpClient;

public class example {
    public static void main(String[] args){
        Subscriber<String> exampleSubscriber = new ExampleSubscriber(5);

        Connection connection = TcpClient.create()
                .wiretap(true)
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
                .subscribe(System.out::println);

        connection.onDispose().block();

//        Flux<String> flux = Flux.generate(
//                () -> 0,
//                (state, sink) -> {
//                    sink.next("sink: " + state);
//                    if (state == 20) sink.complete();
//                    return state + 1;
//                }
//        );
//
//        flux.subscribe(System.out::println);
//
//        Flux<Integer> fluxAsync = Flux.create(
//                sink -> {
//                    sink.next(1);
//                    sink.complete();
//                }
//        );
//
//        fluxAsync.subscribe(System.out::println);
    }
}
