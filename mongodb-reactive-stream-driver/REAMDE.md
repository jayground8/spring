# MongoDB Reactive Stream Driver

R2DBC driver인 r2dbc-mysql과 r2dbc-postgresql는 reactor-netty의 TcpClient를 사용하여 구현한 것을 확인하였다. 그리고 어떻게 BackPressure이 작동하는지 궁금하여 소스코드를 보면서 작동방식을 확인해보았다.<sup>[1][1]</sup> Reactive Stream의 BackPressure 메카니즘을 통해서 Consumer가 처리할 수 있는 만큼 request n하여 Producer로부터 받아 올 수 있다. 하지만 reactor-netty의 TcpClient는 데이터베이스가 쿼리 결과값은 계속해서 Socket receive buffer에 쌓이게 되고, Channel read가 발생할 때 Queue에 쌓이도록 작동한다. 그리고 최종 Consumer가 요청하는 request n 만큼 Queue에서 가져와서 처리한다. 따라서 실질적으로 Consumer가 요청하는 만큼 Producer(데이터베이스)가 데이터를 전달하는 것이 아니다.

데이터베이스에 Select Query를 했을 때, 결과값으로 100개의 row들이 전달된다고 가정해보자. R2DBC를 통해서 5개씩 request를 해서 처리하도록 하더라도 데이터베이스는 Select Query의 결과값인 100개의 row들을 TCP 통신을 통해서 전달을 다 할 것이고, R2DBC를 사용하고 있는 Consumer application에서는 Socket receiver buffer로부터 Queue에 쌓여있는 데이터에서 5개씩 가져와서 처리하게 되는 것이다. 따라서 congestion window, receive window 등의 TCP flow control에 의존하여 BackPressure 메카니즘이 작동하게 된다.

실질적으로 데이터베이스가 보내는 row 갯수를 Cursor를 이용해서 조절할 수 있다. JDBC를 보면 fetch size를 통해서 데이터베이스에서 몇개의 row들을 가져올지 정할 수 있다.

## fetch size with JDBC

아랫처럼 간단하게 JDBC를 통해서 MySQL에 SELECT Query를 하도록 작성하였다. `stmt.setFetchSize(1)`로 `fetch size`를 1로 설정하였다.

```java
Connection conn = DriverManager.getConnection(
                   "jdbc:mysql://127.0.0.1:3306/test?useCursorFetch=true",
                   "root",
                   "my-secret-pw");
Statement stmt = conn.createStatement();
stmt.setFetchSize(1);
ResultSet rs = stmt.executeQuery("SELECT * FROM person");
while (rs.next()) {
    String firstName = rs.getString("first_name");
    String lastName = rs.getString("last_name");
    System.out.println(firstName + " " + lastName);
}
```

Wireshark로 패킷을 확인해보자. 친절하게 MySQL Protocol을 해석해서 보여준다. `setFetchSize(1)`일 경우, `Cursor`를 통해서 `fetch size`만큼 가져오는 걸 확인할 수 있다.

```
Command: Prepare Statement
Statement: SELECT * FROM person
```

```
Command: Execute Statement
Statement ID: 1
Flags: Read-only cursor (1)
Iterations (unused): 1
```

데이터베이스에 Fetch command를 Query result N개 만큼 하게 된다.
```
Command: Fetch Data
Statement ID: 1
Rows to fetch: 1
```

반면에 fetch size의 default값이 0으로 하면 한번의 요청으로 Query 결과값을 가져오고 단순하게 Query를 하는 것을 확인할 수 있다.

```
Command: Query
Statement: SELECT * FROM person
```

## fetch size with R2DBC

r2dbc-postgresql driver를 사용하셔 간단하게 Query를 하는 코드를 예제<sup>[2][2]</sup>를 따라서 아래처럼 작성하였다. fetchSize를 1로 설정하면 Database Cursor(PostgreSQL에서는 Cursor와 조금 다른 Portal이 사용된다)를 사용해서 하나씩 row를 받게 된다.

```java
PostgresqlConnectionFactory connectionFactory = new PostgresqlConnectionFactory(
        PostgresqlConnectionConfiguration.builder()
                .host("127.0.0.1")
                .port(5432)
                .username("postgres")
                .password("helloworld")
                .database("test")
                .build()
);

Mono<PostgresqlConnection> connectionMono = Mono.from(connectionFactory.create());

List<List<Object>> result = connectionMono.flatMapMany(connection ->
        connection
        .createStatement("select * from person")
                .fetchSize(fetchSize)
        .execute()
        .concatMap(it -> it.map(r -> r.get("name")))
).buffer().collectList().block();

System.out.println(result);
```

PostgreSQL의 Client/Server Protocol을 자세히는 모르지만, Wireshark를 통해서 packet을 확인하면 Portal를 사용하여 row 한 개씩 받아오는 것을 확인할 수 있다.

```
Type: Execute
Length: 12
Portal: B_0
Returns: 1 rows
```

```
Type: Portal suspendeed
Length: 4
```

동일하게 0으로 설정하여 모든 row들을 한 요청으로 가져오도록 하면 Simple Query를 하게 된다.

```
Type: Simple query
Length: 25
Query: select * from person
```

## MongoDB

MongoDB에서는 공식적으로 `MongoDB Java Reactive Streams Driver`<sup>[3][3]</sup>를 제공한다. 해당 driver의 Source code<sup>[4][4]</sup>의 BatchCursorFlux에서 아래와 같이 작성되어 있다. MongoDB에서는 `cursor.batchSize(size)`로 설정할 수 있는데, 아랫처럼 Consumer가 요청한 request n 갯수를 통해서 이 batch size를 설정하는 것을 볼 수 있다.

BatchCursorFlux.java
```java
...필요한 부분 빼고 생략
@Override
public void subscribe(final Subscriber<? super T> subscriber) {
    Flux.<T>create(sink -> {
        this.sink = sink;
        sink.onRequest(demand -> {
            if (calculateDemand(demand) > 0 && inProgress.compareAndSet(false, true)) {
                if (batchCursor == null) {
                    int batchSize = calculateBatchSize(sink.requestedFromDownstream());
                    batchCursorPublisher.batchCursor(batchSize).subscribe(bc -> {
                        batchCursor = bc;
                        inProgress.set(false);

                        // Handle any cancelled subscriptions that happen during the time it takes to get the batchCursor
                        if (sink.isCancelled()) {
                            closeCursor();
                        } else {
                            recurseCursor();
                        }
                    }, sink::error);
                } else {
                    inProgress.set(false);
                    recurseCursor();
                }
            }
        });
        sink.onCancel(this::closeCursor);
        sink.onDispose(this::closeCursor);
    }, FluxSink.OverflowStrategy.BUFFER)
    .subscribe(subscriber);
}
```

```java
public class BatchCursor<T> implements AutoCloseable {
...필요한 부분 빼고 생략

    public void setBatchSize(final int batchSize) {
        wrapped.setBatchSize(batchSize);
    }
}
```

# 결론

데이터베이스가 실질적으로 보내는 데이터가 Consumer가 요청한 갯수 n 만큼 보낼 수 있어야지 진정으로 push-pull model이 만들어질 수 있다. 하지만 r2dbc-mysql과 r2dbc-postgresql는 Cursor로 한번에 fetch 하는 row 갯수를 지정할 수는 있지만, Consumer의 request에 대해서 dynamic하게 적용하는 로직은 보이지 않는다. 데이터베이스는 Query의 모든 결과값을 한번에 보내게 되고, client단의 reactor-netty로 request한 만큼 Queue에서 가져오는 방식으로 작동하게 된다. 따라서 TCP flow contorl에 의존하여 backpressure가 작동하게 된다. 한편 MongoDB의 공식 reactive stream driver에서는 Cursor의 fetch size를 Consumer의 request 값을 바탕으로 설정하는 것을 확인할 수 있었다.

fetch size를 0일 때는 Simple Query로 모든 데이터를 한번의 요청에 가져올 수 있었다. 하지만 fetch size가 0 이상일때는 mysql과 postgresql는 cursor를 사용하게 되고, 또한 prepared statement를 이용하는 것을 확인할 수 있었다. 한번의 request로 전체 데이터를 가지고 오지 않고, fetch size만큼 여러번 통신을 통해서 가지고 오는 것은 전체 데이터 기준으로 더 많은 시간이 소요될 것이다. 그리고 prepared statement는 bind되는 값만 바뀌고 반복되는 Query가 사용된다면 유리하지만, 이것도 Prepare, Execute 두번의 스텝으로 작동하지 때문에 두번의 통신이 필요하게 된다.

많은 데이터가 있고, 일부의 데이터만 사용하고 끝날 수 있는 비지니스 로직이라면 데이터베이스에서 fetch size를 설정하면 조금씩 가져오는 것이 합리적일 것이다. 그리고 Consumer의 상태에 따라서 dynamic하게 요청할 수 있으면 더 좋을 것이다. 하지만 많은 경우에서는 simple query로 필요한 데이터를 한번의 요청으로 다 가져와서 처리하는 것이 합리적일 것이다.

## 부록 A : use cases 조사

from 우아한 형제들 기술블로그

[2020.2.19 가게 노출 시스템에 Webflux를 적용한 이야기](https://techblog.woowahan.com/2667/) 에서는 외부 요청이 많기 때문에 기존 Blocking & thread pool model에서 Webflux를 사용하였다. 그리고 SQS에서 Event를 가져와서 처리하는 부분이 있는데, 장애시 쌓여있던 Event가 한꺼번에 쏟아지면서 문제가 있었다고 한다. 그래서 delaySequence와 limitRate로 처리량 조절을 했다고 한다. 🤔 **push only mode로만 작동하는 상황이였기 때문에, delaySequence와 limitRate을 사용한 거겠지?**

## 부록 B : 사용한 docker image

```bash
docker run --name jdbc-mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=my-secret-pw -d mysql:5.6
```

```bash
docker run --name my-psql -d -p 5432:5432 -e POSTGRES_PASSWORD=helloworld postgres
```

[1]: https://github.com/jayground8/spring/tree/main/reactor-netty-core

[2]: https://github.com/pgjdbc/r2dbc-postgresql

[3]: https://www.mongodb.com/docs/drivers/reactive-streams/

[4]: https://github.com/mongodb/mongo-java-driver/blob/master/driver-reactive-streams/src/main/com/mongodb/reactivestreams/client/internal/BatchCursorFlux.java