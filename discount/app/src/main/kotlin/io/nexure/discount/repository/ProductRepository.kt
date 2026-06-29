package io.nexure.discount.repository

import com.mongodb.client.model.Filters
import com.mongodb.client.model.IndexOptions
import com.mongodb.client.model.Indexes
import com.mongodb.client.model.ReplaceOptions
import com.mongodb.client.model.Updates
import com.mongodb.kotlin.client.coroutine.MongoClient
import com.mongodb.kotlin.client.coroutine.MongoDatabase
import io.nexure.discount.model.Discount
import io.nexure.discount.model.Product
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.toList

class ProductRepository(mongoClient: MongoClient, databaseName: String = "productdb") {
    private val database: MongoDatabase = mongoClient.getDatabase(databaseName)
    private val collection = database.getCollection<Product>("products")
    
    suspend fun init() {
        collection.createIndex(
            Indexes.ascending("id"),
            IndexOptions().unique(true)
        )
    }
    
    suspend fun save(product: Product): Product {
        // upsert in one shot instead of find-then-insert-or-replace, that had its own small race
        // window between the check and the write
        val filter = Filters.eq("id", product.id)
        collection.replaceOne(filter, product, ReplaceOptions().upsert(true))
        return product
    }
    
    suspend fun findById(id: String): Product? {
        return collection.find(Filters.eq("id", id)).firstOrNull()
    }
    
    suspend fun findByCountry(country: String): List<Product> {
        return collection.find(Filters.eq("country", country)).toList()
    }
    
    suspend fun applyDiscount(productId: String, discount: Discount): Product? {
        // Atomic conditional update prevents duplicate discounts and lost updates
        // during concurrent requests.
        val filter = Filters.and(
            Filters.eq("id", productId),
            Filters.not(Filters.elemMatch("discounts", Filters.eq("discountId", discount.discountId)))
        )
        val update = Updates.push("discounts", discount)

        collection.updateOne(filter, update)

        return findById(productId)
    }
    
    suspend fun deleteById(id: String): Boolean {
        val result = collection.deleteOne(Filters.eq("id", id))
        return result.deletedCount > 0
    }

    suspend fun deleteAll() {
        collection.drop()
    }
}
