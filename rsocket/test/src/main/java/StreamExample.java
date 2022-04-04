import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketConnector;
import io.rsocket.core.RSocketServer;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;

import java.time.Duration;

public class StreamExample {
    public static void main(String[] args) throws InterruptedException {
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

        Thread.sleep(1000000);
    }
}
