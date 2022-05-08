package com.example.sample.repositories;

import com.example.sample.entities.Cat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.QueryHints;

import javax.persistence.QueryHint;
import java.util.stream.Stream;

import static org.hibernate.jpa.QueryHints.HINT_FETCH_SIZE;
public interface CatRepositoryStream extends JpaRepository<Cat, Long> {
//    @QueryHints(value={@QueryHint(name = "org.hibernate.fetchSize", value = "1")})
//    @QueryHints(@javax.persistence.QueryHint(name="org.hibernate.annotations.QueryHints.FETCH_SIZE", value="" + Integer.MIN_VALUE))
    @QueryHints(value=@QueryHint(name = HINT_FETCH_SIZE, value = "" + Integer.MIN_VALUE))
    Stream<Cat> findAllByName(String name);
}
