# Stream in Srping Data JPA

Database와 data를 주고 받는 경우, pull push 관점에서 다음과 같은 세 가지를 고민하였다.
1. row 하나씩 request/response로 가져온다.
2. batch로 여러개의 row들을 request/response로 가져온다.
3. 한번의 request를 하고 stream으로 계속 해서 row들을 받아온다.

## Stream

JDBC ResultSet는 이제 Heap에 올라가게 된다. JDBC driver을 전체 결과값을 ResultSet으로 가져오지 않고, row 하나씩 있는 ResultSet을 stream으로 가져오게 할 수 있다. 이렇게 설정하지 않으면, Stream객체로 설정하더라도 전체 결과값을 담은 ResultSet이 heap에 올라가고 Stream API를 통해서 iteration를 하게 된다. 따라서 Stream 객체를 활용하더라도 아주 큰 결과값이 필요한 Query를 하게 되면, 여전히 OOM의 가능성이 동일하게 존재한다.

> The combination of a forward-only, read-only result set, with a fetch size of Integer.MIN_VALUE serves as a signal to the driver to stream result sets row-by-row. After this, any result sets created with the statement will be retrieved row-by-row. [from mysql doc](https://dev.mysql.com/doc/connector-j/5.1/en/connector-j-reference-implementation-notes.html)

```java
@QueryHints(value=@QueryHint(name = HINT_FETCH_SIZE, value = "" + Integer.MIN_VALUE))
Stream<Cat> findAllByName(String name);
```

```java
@GetMapping("/stream/cats")
@Transactional(readOnly = true)
public List<Cat> getStream(@RequestParam String name) {
```

Stream객체를 사용하면 3번처럼 한번의 요청으로 Database에서 stream으로 결과값을 받는 것처럼 생각할 수 있지만, Database가 직접 stream으로 row하나씩 전달하는 것은 아니다.

MySQL Wire Protocol에서 하나의 packet에 row들을 최대한 담아서 보내는데, 이제 Query result가 크면 packet 여러 개에 담아서 보내게 된다. 기본적으로 전체 packet을 다 받아서 하나의 거대한 ResultSet으로 만들게 된다. _Stream방식이 먼저 도착한 packet의 row들을 ResultSet으로 전달한다면_, row하나씩 컨트롤은 아니지만 적어도 packet size별로 컨트롤을 가능할 것이다.

## Batch size

cursor based streaming 방법을 사용할 수도 있다. 이는 Database로부터 batch size로 정한 갯수만큼 row를 받게 된다.

1. Request Prepared Statement
2. Execute Prepared Statement with Cursor (flag CURSOR_TYPE_READ_ONLY)
3. Repeat fetch request (fetch size)

만약 database에서 row 하나씩 받고 싶어서 batch size를 1로 한다고 하면, `1. item 하나씩 request/response로 가져온다.`처럼 fetch request하여 하나의 row를 response를 받아오고, 다시 fetch request를 하여 하나의 row를 받아오는 것을 반복하게 된다.

이렇게 cursor를 활용하기 위해서는 `?useCursorFetch=true`를 설정해줘야한다.

```java
spring.datasource.url=jdbc:mysql://localhost:3306/test?useCursorFetch=true
```

하지만 이렇게 `?useCursorFetch=true`를 설정하게 되면, _다른 Query가 Simple Query가 아니라 Prepared Statement를 사용하게 된다._ (Prepared Statement without cursor)

일부로 `sleep`을 걸어서 지연시켜서 확인을 해보니, stream으로 처리를 하고 나면 그다음에 fetch request로 다음 row를 가져오게 된다. 따라서 어떠한 경우에 의해서 `stream.close()`를 하게 된다면 더이상 Database로부터 data를 받지 않고 중단할 수 있게 된다.

```java
Stream<Cat> stream = catRepositoryStream.findAllByName(name);
return stream
        .map(cat -> {
            try {
                System.out.println(cat.toString());
                Thread.sleep(10000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (cat.getAge() == 2) stream.close();
            return cat;
        })
        .onClose(() -> System.out.println("stream has closed!"))
        .collect(Collectors.toList());
```

## 결론

JDBC driver에게 stream으로 ResultSet을 row하나씩 보내도록 요청하더라도 Database로부터는 Query 결과를 batch로 가져오게 된다. Database server가 row별로 stream으로 보내주는 것이 아니라 driver에서 ResultSet에 row하나씩 담아서 전달해주는 것이다.

batch size를 사용한다면 Stream에서 다음 row data가 필요할 때 batch request를 통해서 필요한 만큼 Database로부터 가져올 수 있다. 하지만 이것은 request/response 통신 횟수를 증가시킨다.

Reactive Stream을 사용하는 R2DBC driver의 경우에 많은 driver가 `reactor-netty`를 사용하고 있다. Reactive Stream으로 database로부터 원하는 갯수만큼 request하더라도 이것은 Database에서 그 갯수만큼 받아오는 것이 아니다. Netty의 ByteBuf로부터 receive socket의 data를 읽고 그것을 Queue에 저장해놓았다가 request를 하는 만큼 Queue에서 가져오는 것이다.