package com.example.demo

import com.github.javafaker.Faker
import io.r2dbc.spi.ConnectionFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.io.ClassPathResource
import org.springframework.data.annotation.Id
import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.r2dbc.repository.R2dbcRepository
import org.springframework.data.relational.core.mapping.Table
import org.springframework.http.MediaType
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator
import org.springframework.stereotype.Repository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter
import reactor.core.publisher.Flux
import reactor.core.scheduler.Schedulers
import java.util.concurrent.Executors


fun generateFakeData(n: Int) : MutableList<Cat> {
	val cats = mutableListOf<Cat>()
	for (i in 1 .. n) {
		val faker = Faker()
		cats.add(Cat(name = faker.cat().name(), breed = faker.cat().breed()))
	}

	return cats
}

@SpringBootApplication
class DemoApplication
//class DemoApplication : CommandLineRunner {
//	@Autowired
//	private lateinit var catRepository: CatRepository
//
//	@Bean
//	fun initializer(@Qualifier("connectionFactory") connectionFactory: ConnectionFactory) : ConnectionFactoryInitializer {
//		val initializer = ConnectionFactoryInitializer()
//		initializer.setConnectionFactory(connectionFactory)
//		val populator = CompositeDatabasePopulator()
//		populator.addPopulators(ResourceDatabasePopulator(ClassPathResource("schema.sql")))
////		populator.addPopulators(ResourceDatabasePopulator(ClassPathResource("data.sql")))
//		initializer.setDatabasePopulator(populator)
//		return initializer
//	}
//
//	override fun run(vararg args: String?) {
//		for (arg in args)
//			if (arg.equals("init-db")) {
//				catRepository.saveAll(generateFakeData(500)).subscribe()
//			}
//
//		val cats = catRepository
//				.getLimit()
//				.subscribeOn(Schedulers.boundedElastic())
//				.buffer().blockLast()
//		print(cats)
//	}
//}

fun main(args: Array<String>) {
	runApplication<DemoApplication>(*args)
}

@RestController
class RestController(var catRepository: CatRepository) {

	@GetMapping("/cats")
	fun getCats() : MutableList<Cat>? {
		return catRepository
				.getLimit()
				.subscribeOn(Schedulers.boundedElastic())
				.buffer()
				.map{
					println(Thread.currentThread().name)
					it
				}.blockLast()
	}

	@GetMapping("/flux")
	fun getFlux() : Flux<Cat> {
		return catRepository.findAll().take(5)
	}

	@GetMapping("/emitter")
	fun getEmitter() = ResponseBodyEmitter().apply {
		this.send("hello world!")
		this.complete()
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

@Table("cats")
data class Cat(
		@Id val id: Int?=null,
		val name: String,
		val breed: String
)

