package com.example.sample

import io.r2dbc.spi.Row
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toCollection
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.reactive.asFlow
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.data.relational.core.mapping.Table
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitSingle
import org.springframework.stereotype.Component
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.util.function.BiFunction


@SpringBootApplication
class SampleApplication

fun main(args: Array<String>) {
	runApplication<SampleApplication>(*args)
}

@RestController
class RestController(var catRepository: CatRepository, val catRepositoryCoroutine: CatRepositoryCoroutine) {

	@GetMapping("/cats")
	fun kgetCats(): Flux<Cat> {
		return catRepository.findAll()
	}

	@GetMapping("/cats/{id}")
	fun kgetCatById(@PathVariable id: Int): Mono<Cat> {
		return catRepository.findById(id)
	}

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

	@GetMapping("/coroutine/unconfined")
	suspend fun getNonBlock(): String {
		println("a ${Thread.currentThread().name}")
		delay(3000)
		println("b ${Thread.currentThread().name}")
		return "unconfined"
	}
}

@Repository
interface CatRepository : R2dbcRepository<Cat, Int> {
	@Query("SELECT * FROM cats LIMIT 10")
	fun getLimit(): Flux<Cat>

	@Query("SELECT * FROM cats WHERE name = $1")
	fun findAllByName(name: String): Flux<Cat>

	@Query("SELECT * FROM cats WHERE breed = $1")
	fun findAllByBreed(breed: String): Flux<Cat>
}

@Component
class CatMapper: BiFunction<Row, Any, Cat> {
	override fun apply(row: Row, u: Any): Cat {
		return Cat(
			row.get("id", Number::class.java)?.toInt(),
			row.get("name", String::class.java) ?: "",
			row.get("breed", String::class.java) ?: ""
		)
	}
}

@Repository
class CatRepositoryCoroutine(private val client: DatabaseClient, private val mapper: CatMapper) {

	suspend fun getCatById(id: Int): Cat? {
		return client
				.sql("SELECT * FROM cats WHERE id = $1")
				.bind(0, id)
				.map(mapper::apply)
				.awaitSingle()
	}
}

@Table("cats")
data class Cat(
	@Id val id: Int?=null,
	val name: String,
	val breed: String
)