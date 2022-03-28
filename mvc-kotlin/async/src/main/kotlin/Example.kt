import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlinx.coroutines.future.await
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.lang.Thread.sleep
import java.util.concurrent.CompletableFuture


/* CompletableFuture를 이용 */

fun getData(name: String): CompletableFuture<String> {
    return CompletableFuture.supplyAsync {
        sleep(10000)
        name
    }
}

fun main() = runBlocking {
    val a = async { getData("Durian").await() }
    val b = async { getData("Coco").await() }
    val c = async { getData("Jerry").await() }
    val result = listOf<String>(a.await(), b.await(), c.await())
    println(result)
}


/* WebClient 이용 */

//val client = WebClient.create("http://localhost:8080")
//
//fun main() = runBlocking() {
//    val a = async {
//        client.get().uri("/?name=Durian").retrieve().awaitBody<String>()
//    }
//
//    val b = async(Dispatchers.Default) {
//        client.get().uri("/?name=Coco").retrieve().awaitBody<String>()
//    }
//
//    val c = async(Dispatchers.Default)   {
//        client.get().uri("/?name=Jerry").retrieve().awaitBody<String>()
//    }
//
//    val messages = listOf<String>(a.await(), b.await(), c.await())
//    println(messages)
//}

//suspend fun main() =  coroutineScope{
//    val a = async {
//        client.get().uri("/").retrieve().awaitBody<String>()
//    }
//
//    val b = async(Dispatchers.Default) {
//        client.get().uri("/").retrieve().awaitBody<String>()
//    }
//
//    val c = async(Dispatchers.Default)   {
//        client.get().uri("/").retrieve().awaitBody<String>()
//    }
//
//    val messages = listOf<String>(a.await(), b.await(), c.await())
//    println(messages)
//}

/* Ktor Client 이용 */

//suspend fun getResult(client: HttpClient, name: String): String {
//    val response: HttpResponse = client.get("http://localhost:8080/?name=${name}")
//    return response.receive()
//}
//
//fun main() = runBlocking(Dispatchers.Unconfined) {
//    val client = HttpClient(CIO)
//    val a = async { getResult(client, "Durian")}
//    val b = async { getResult(client, "Coco")}
//    val c = async { getResult(client, "Jerry")}
//    val result = listOf(a.await(), b.await(), c.await())
//    println(result)
//    client.close()
//}