package io.nexure.discount

import com.mongodb.kotlin.client.coroutine.MongoClient
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import io.nexure.discount.model.ApplyDiscountRequest
import io.nexure.discount.model.CreateProductRequest
import io.nexure.discount.model.Discount
import io.nexure.discount.repository.ProductRepository
import io.nexure.discount.service.ProductAlreadyExistsException
import io.nexure.discount.service.ProductService
import io.nexure.discount.service.UnsupportedCountryException

const val PRODUCTS_ENDPOINT = "/products"
const val PRODUCT_BY_ID_ENDPOINT = "/products/{id}"
const val PRODUCT_DISCOUNT_ENDPOINT = "/products/{id}/discount"

fun main() {
    embeddedServer(
        factory = Netty,
        port = 4040,
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
    val log = environment.log
    monitor.subscribe(ApplicationStarted) {
        try {
            kotlinx.coroutines.runBlocking {
                repository.init()
            }
            log.info("MongoDB repository initialized (uri={})", mongoConnectionString)
        } catch (e: Exception) {
            // ktor just swallows exceptions thrown from event listeners, so without this catch
            // the app looks "stuck" with zero feedback if mongo isn't reachable
            log.error("Failed to initialize MongoDB repository (uri={}). Is MongoDB running?", mongoConnectionString, e)
        }
    }

    install(ContentNegotiation) {
        json()
    }

    routing {
        get(PRODUCTS_ENDPOINT) {
            val country = call.request.queryParameters["country"]
            if (country == null) {
                call.respond(HttpStatusCode.BadRequest, "Country parameter is required")
                return@get
            }

            try {
                val products = service.getProductsByCountry(country)
                call.respond(products)
            } catch (e: UnsupportedCountryException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Unsupported country")
            }
        }

        // extra endpoints for manual testing - create/get/delete a product directly
        post(PRODUCTS_ENDPOINT) {
            val request = call.receive<CreateProductRequest>()
            try {
                val product = service.createProduct(
                    id = request.id,
                    name = request.name,
                    basePrice = request.basePrice,
                    country = request.country
                )
                call.respond(HttpStatusCode.Created, product)
            } catch (e: UnsupportedCountryException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Unsupported country")
            } catch (e: ProductAlreadyExistsException) {
                call.respond(HttpStatusCode.Conflict, e.message ?: "Product already exists")
            }
        }

        get(PRODUCT_BY_ID_ENDPOINT) {
            val productId = call.parameters["id"]
            if (productId == null) {
                call.respond(HttpStatusCode.BadRequest, "Product ID is required")
                return@get
            }

            try {
                val product = service.getProductById(productId)
                if (product == null) {
                    call.respond(HttpStatusCode.NotFound, "Product not found")
                } else {
                    call.respond(product)
                }
            } catch (e: UnsupportedCountryException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Unsupported country")
            }
        }

        delete(PRODUCT_BY_ID_ENDPOINT) {
            val productId = call.parameters["id"]
            if (productId == null) {
                call.respond(HttpStatusCode.BadRequest, "Product ID is required")
                return@delete
            }

            val deleted = service.deleteProduct(productId)
            if (deleted) {
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.NotFound, "Product not found")
            }
        }

        put(PRODUCT_DISCOUNT_ENDPOINT) {
            val productId = call.parameters["id"]
            if (productId == null) {
                call.respond(HttpStatusCode.BadRequest, "Product ID is required")
                return@put
            }

            val request = call.receive<ApplyDiscountRequest>()
            try {
                val product = service.applyDiscount(productId, Discount(request.discountId, request.percent))

                if (product == null) {
                    call.respond(HttpStatusCode.NotFound, "Product not found")
                } else {
                    call.respond(product)
                }
            } catch (e: UnsupportedCountryException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Unsupported country")
            }
        }
    }
}
