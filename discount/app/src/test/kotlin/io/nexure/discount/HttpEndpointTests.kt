package io.nexure.discount

import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.testApplication
import io.nexure.discount.model.ApplyDiscountRequest
import io.nexure.discount.model.ProductResponse
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * HTTP Endpoint Tests
 * 
 * These tests verify that the HTTP endpoints work correctly.
 * If you see serialization errors, check the Application configuration!
 */
class HttpEndpointTests {
    
    companion object {
        private val mongoContainer = MongoDBContainer(DockerImageName.parse("mongo:7.0"))
        
        init {
            mongoContainer.start()
        }
    }
    
    @Test
    fun `GET products endpoint should return JSON`() = testApplication {
        environment {
            config = MapApplicationConfig("mongodb.uri" to mongoContainer.connectionString)
        }
        application {
            module()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        
        val response = client.get("/products?country=Sweden")
        
        assertEquals(HttpStatusCode.OK, response.status,
            "Should return 200 OK. Check if JSON serialization is properly configured.")
        
        val products = response.body<List<ProductResponse>>()
        assertNotNull(products)
    }
    
    @Test
    fun `PUT discount endpoint should accept and return JSON`() = testApplication {
        environment {
            config = MapApplicationConfig("mongodb.uri" to mongoContainer.connectionString)
        }
        application {
            module()
        }
        
        val client = createClient {
            install(ContentNegotiation) {
                json()
            }
        }
        
        val response = client.put("/products/test-id/discount") {
            contentType(ContentType.Application.Json)
            setBody(ApplyDiscountRequest("TEST10", 10.0))
        }
        
        assertEquals(HttpStatusCode.NotFound, response.status,
            "Should return 404 for non-existent product.")
    }
    
    @Test
    fun `should return 400 when country parameter is missing`() = testApplication {
        environment {
            config = MapApplicationConfig("mongodb.uri" to mongoContainer.connectionString)
        }
        application {
            module()
        }
        
        val response = client.get("/products")
        
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
