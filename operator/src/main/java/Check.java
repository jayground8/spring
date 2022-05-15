import reactor.core.publisher.Operators;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

class Sample {
    volatile long requested;
    @SuppressWarnings("rawtypes")
    static final AtomicLongFieldUpdater<Sample> REQUESTED =
            AtomicLongFieldUpdater.newUpdater(Sample.class, "requested");

    public void exampleCap() {
        Operators.addCap(REQUESTED, this, 5);
    }

    public void exampleProduce() {
        Operators.produced(REQUESTED, this, 1);
    }

    public long getRequested() {
        return requested;
    }
}

public class Check {
    public static void main(String[] args) {
        Sample sample = new Sample();

        sample.exampleCap();
        sample.exampleProduce();
        sample.exampleProduce();
        sample.exampleProduce();
        sample.exampleProduce();
        sample.exampleProduce();
        sample.exampleProduce();
        sample.exampleProduce();

        System.out.println(sample.getRequested());
    }
}
