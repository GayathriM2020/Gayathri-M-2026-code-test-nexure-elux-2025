package io.nexure.discount

import com.mongodb.kotlin.client.coroutine.MongoClient
import io.nexure.discount.model.Discount
import io.nexure.discount.model.Product
import io.nexure.discount.repository.ProductRepository
import io.nexure.discount.service.ProductService
import io.nexure.discount.service.UnsupportedCountryException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.utility.DockerImageName
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test suite for the Product Discount Service.
 * 
 * Your job is to fix the implementation to make these tests pass.
 * If you identify tests with incorrect expectations, you may modify them (document why!).
 */
class ProductServiceTests {
    
    companion object {
        private val mongoContainer = MongoDBContainer(DockerImageName.parse("mongo:7.0"))
        private lateinit var mongoClient: MongoClient
        
        init {
            mongoContainer.start()
            mongoClient = MongoClient.create(mongoContainer.connectionString)
        }
    }
    
    private lateinit var repository: ProductRepository
    private lateinit var service: ProductService
    
    @BeforeTest
    fun setup() = runBlocking {
        repository = ProductRepository(mongoClient, "testdb_${System.currentTimeMillis()}")
        service = ProductService(repository)
        repository.init()
    }
    
    @AfterTest
    fun tearDown() = runBlocking {
        repository.deleteAll()
    }
    
    @Test
    fun `should calculate final price correctly with single discount`() = runBlocking {
        // Setup
        repository.save(
            Product(
                id = "prod-1",
                name = "Laptop",
                basePrice = 1000.0,
                country = "Sweden",
                discounts = listOf(Discount("SUMMER10", 10.0))
            )
        )
        
        // Test
        val products = service.getProductsByCountry("Sweden")
        
        // Assert
        assertEquals(1, products.size)
        val product = products[0]
        assertEquals("prod-1", product.id)
        assertEquals(1, product.discounts.size)
        
        // Expected: 1000 * (1 - 0.10) * (1 + 0.25) = 1000 * 0.9 * 1.25 = 1125.0
        assertEquals(1125.0, product.finalPrice, 0.01, 
            "Final price calculation is incorrect for single discount. " +
            "Formula: basePrice × (1 - discount) × (1 + VAT)")
    }
    
    @Test
    fun `should calculate final price correctly with multiple discounts`() = runBlocking {
        // Setup: Product with TWO discounts
        repository.save(
            Product(
                id = "prod-2",
                name = "Phone",
                basePrice = 500.0,
                country = "Germany",
                discounts = listOf(
                    Discount("DISCOUNT10", 10.0),
                    Discount("DISCOUNT20", 20.0)
                )
            )
        )
        
        // Test
        val products = service.getProductsByCountry("Germany")
        
        // Assert
        assertEquals(1, products.size)
        val product = products[0]
        assertEquals(2, product.discounts.size)
        
        assertEquals(428.40, product.finalPrice, 0.01,
            "Multiple discounts MUST be applied multiplicatively, not additively! " +
            "Correct: basePrice × (1-d1) × (1-d2) × ... × (1+VAT)")
    }
    
    @Test
    fun `should apply discount to product correctly`() = runBlocking {
        // Setup
        repository.save(
            Product(
                id = "prod-3",
                name = "Tablet",
                basePrice = 800.0,
                country = "France",
                discounts = emptyList()
            )
        )
        
        // Test: Apply a discount
        val result = service.applyDiscount("prod-3", Discount("NEWYEAR15", 15.0))
        
        // Assert
        assertNotNull(result)
        assertEquals("prod-3", result.id)
        assertEquals(1, result.discounts.size)
        assertEquals("NEWYEAR15", result.discounts[0].discountId)
        assertEquals(15.0, result.discounts[0].percent)
        
        // Expected: 800 * (1 - 0.15) * (1 + 0.20) = 800 * 0.85 * 1.20 = 816.0
        assertEquals(816.0, result.finalPrice, 0.01)
    }
    
    @Test
    fun `should be idempotent when applying same discount twice sequentially`() = runBlocking {
        // Setup
        repository.save(
            Product(
                id = "prod-4",
                name = "Monitor",
                basePrice = 300.0,
                country = "Sweden",
                discounts = emptyList()
            )
        )
        
        val discount = Discount("REPEAT10", 10.0)
        
        // Test: Apply discount twice
        val result1 = service.applyDiscount("prod-4", discount)
        val result2 = service.applyDiscount("prod-4", discount)
        
        // Assert: Second application should not change anything (idempotency)
        assertNotNull(result1)
        assertNotNull(result2)
        assertEquals(1, result1.discounts.size)
        assertEquals(1, result2.discounts.size)
        assertEquals(result1.finalPrice, result2.finalPrice, 0.01,
            "Applying the same discount twice should be idempotent (no change on second application)")
    }
    
    @Test
    fun `should handle concurrent discount applications safely - NO DUPLICATES`() = runBlocking {
        // Setup
        repository.save(
            Product(
                id = "prod-5",
                name = "Keyboard",
                basePrice = 100.0,
                country = "Germany",
                discounts = emptyList()
            )
        )
        
        val idempotencyJobs = List(25) {
            async(Dispatchers.IO) {
                service.applyDiscount("prod-5", Discount("SAME_DISCOUNT", 5.0))
            }
        }

        val idempotencyResults = idempotencyJobs.awaitAll()
        idempotencyResults.forEach { result ->
            assertNotNull(result)
        }

        var product = repository.findById("prod-5")
        assertNotNull(product)
        assertEquals(1, product.discounts.size)
        assertEquals("SAME_DISCOUNT", product.discounts[0].discountId)

        val concurrencyJobs = List(25) { index ->
            async(Dispatchers.IO) {
                service.applyDiscount("prod-5", Discount("CONCURRENT_DISCOUNT_${index}", 2.0))
            }
        }
        
        val concurrencyResults = concurrencyJobs.awaitAll()
        concurrencyResults.forEach { result ->
            assertNotNull(result)
        }

        product = repository.findById("prod-5")
        assertNotNull(product)
        assertEquals(26, product.discounts.size)

        val discountIds = product.discounts.map { it.discountId }.toSet()
        assertEquals(26, discountIds.size)

        assertTrue(discountIds.contains("SAME_DISCOUNT"))

        for (i in 0 until 25) {
            assertTrue(discountIds.contains("CONCURRENT_DISCOUNT_$i"))
        }
    }
    
    @Test
    fun `should return null when applying discount to non-existent product`() = runBlocking {
        // Test
        val result = service.applyDiscount("non-existent", Discount("NOTFOUND10", 10.0))
        
        // Assert
        assertNull(result, "Should return null for non-existent product")
    }
    
    @Test
    fun `should return empty list for country with no products`() = runBlocking {
        // this used to query "NonExistentCountry" - but that's an unsupported country, not a
        // supported one with zero products, which is what this test is actually meant to check.
        // switched to a real supported country with nothing saved for it instead. the
        // unsupported-country case is its own test now (below)
        val products = service.getProductsByCountry("Sweden")

        // Assert
        assertTrue(products.isEmpty(), "Should return empty list for country with no products")
    }
    
    @Test
    fun `should correctly apply VAT rates for different countries`() = runBlocking {
        // Setup products in different countries
        repository.save(Product("s1", "Item", 100.0, "Sweden", emptyList()))
        repository.save(Product("g1", "Item", 100.0, "Germany", emptyList()))
        repository.save(Product("f1", "Item", 100.0, "France", emptyList()))
        
        // Test
        val sweden = service.getProductsByCountry("Sweden")[0]
        val germany = service.getProductsByCountry("Germany")[0]
        val france = service.getProductsByCountry("France")[0]
        
        // Assert: VAT rates: Sweden 25%, Germany 19%, France 20%
        assertEquals(125.0, sweden.finalPrice, 0.01, "Sweden VAT should be 25%")
        assertEquals(119.0, germany.finalPrice, 0.01, "Germany VAT should be 19%")
        assertEquals(120.0, france.finalPrice, 0.01, "France VAT should be 20%")
    }
    
    @Test
    fun `should reject requests for unsupported countries`() = runBlocking {
        // this replaces the old "should handle products from unknown countries" test, which
        // asserted that an unconfigured country (UnitedKingdom) silently worked with 0% VAT.
        // that's the bug the README points at - silently defaulting to 0% VAT means we just
        // never charge VAT for markets we haven't configured, which isn't a safe default.
        // unsupported countries should get rejected instead.
        repository.save(Product("uk1", "Item", 100.0, "UnitedKingdom", emptyList()))

        assertFailsWith<UnsupportedCountryException> {
            service.getProductsByCountry("UnitedKingdom")
        }
    }
}
