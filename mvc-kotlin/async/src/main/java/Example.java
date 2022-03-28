import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.util.List;

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
