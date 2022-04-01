import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.api.PostgresqlConnection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider.OPTIONS;
import static io.r2dbc.spi.ConnectionFactoryOptions.*;

public class Example {
    public static void main(String[] args) {
//        runJDBCExample(1);
        runR2DBCDriver(0);
    }
    private static void runJDBCExample(Integer fetchSize) {
        try {
           Connection conn = DriverManager.getConnection(
                   "jdbc:mysql://127.0.0.1:3306/test?useCursorFetch=true",
                   "root",
                   "my-secret-pw");
           Statement stmt = conn.createStatement();
           stmt.setFetchSize(fetchSize);
           ResultSet rs = stmt.executeQuery("SELECT * FROM person");
           while (rs.next()) {
               String firstName = rs.getString("first_name");
               String lastName = rs.getString("last_name");
               System.out.println(firstName + " " + lastName);
           }

       } catch (Exception e) {
           e.printStackTrace();
       }
    }

    private static void runR2DBCDriver(Integer fetchSize) {
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
    }
}

/*
CREATE TABLE person (
	id serial PRIMARY KEY,
	name VARCHAR ( 50 ) UNIQUE NOT NULL
);
 */