package io.nexure.discount

import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.nexure.discount.model.ApplyDiscountRequest
import io.nexure.discount.model.Discount
import io.nexure.discount.repository.ProductRepository
import io.nexure.discount.service.ProductService

const val PRODUCTS_ENDPOINT = "/products"
const val PRODUCT_DISCOUNT_ENDPOINT = "/products/{id}/discount"

fun main() {
    embeddedServer(
        factory = Netty,
        port = 8082,
        host = "0.0.0.0",
        module = Application::module,
    ).start(true)
}

fun Application.module() {
    val mongoConnectionString = environment.config.propertyOrNull("mongodb.uri")?.getString()
        ?: "mongodb://localhost:27017"
    
    val mongoClient = MongoClient.create(mongoConnectionString)
    val repository = ProductRepository(mongoClient)
    val service = ProductService(repository)
    
    // Initialize repository
    monitor.subscribe(ApplicationStarted) {
        kotlinx.coroutines.runBlocking {
            repository.init()
        }
    }
    
    routing {
        get(PRODUCTS_ENDPOINT) {
            val country = call.request.queryParameters["country"]
            if (country == null) {
                call.respond(HttpStatusCode.BadRequest, "Country parameter is required")
                return@get
            }
            
            val products = service.getProductsByCountry(country)
            call.respond(products)
        }
        
        put(PRODUCT_DISCOUNT_ENDPOINT) {
            val productId = call.parameters["id"]
            if (productId == null) {
                call.respond(HttpStatusCode.BadRequest, "Product ID is required")
                return@put
            }
            
            val request = call.receive<ApplyDiscountRequest>()
            val product = service.applyDiscount(productId, Discount(request.discountId, request.percent))
            
            if (product == null) {
                call.respond(HttpStatusCode.NotFound, "Product not found")
            } else {
                call.respond(product)
            }
        }
    }
}
