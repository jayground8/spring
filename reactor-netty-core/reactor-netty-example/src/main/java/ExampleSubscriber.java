import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

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
