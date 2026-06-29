package io.nexure.discount.service

import io.nexure.discount.config.VatConfig
import io.nexure.discount.model.Discount
import io.nexure.discount.model.DiscountResponse
import io.nexure.discount.model.Product
import io.nexure.discount.model.ProductResponse
import io.nexure.discount.repository.ProductRepository

// thrown when a country isn't in VatConfig - routes should turn this into a 400 instead of
// letting it fall through with an undefined VAT rate
class UnsupportedCountryException(country: String) :
    IllegalArgumentException("Unsupported country: $country")

// thrown by createProduct() if the id is already taken
class ProductAlreadyExistsException(id: String) :
    IllegalStateException("Product already exists: $id")

class ProductService(private val repository: ProductRepository) {

    suspend fun getProductsByCountry(country: String): List<ProductResponse> {
        if (!VatConfig.isSupported(country)) {
            throw UnsupportedCountryException(country)
        }
        return repository.findByCountry(country).map { it.toProductResponse() }
    }

    suspend fun getProductById(id: String): ProductResponse? =
        repository.findById(id)?.toProductResponse()

    suspend fun createProduct(id: String, name: String, basePrice: Double, country: String): ProductResponse {
        if (!VatConfig.isSupported(country)) {
            throw UnsupportedCountryException(country)
        }
        if (repository.findById(id) != null) {
            throw ProductAlreadyExistsException(id)
        }
        val product = Product(id = id, name = name, basePrice = basePrice, country = country)
        return repository.save(product).toProductResponse()
    }

    suspend fun deleteProduct(id: String): Boolean = repository.deleteById(id)

    suspend fun applyDiscount(productId: String, discount: Discount): ProductResponse? =
        repository.applyDiscount(productId, discount)?.toProductResponse()

    private fun Product.toProductResponse(): ProductResponse = ProductResponse(
        id = id,
        name = name,
        basePrice = basePrice,
        country = country,
        discounts = discounts.map { DiscountResponse(it.discountId, it.percent) },
        finalPrice = calculateFinalPrice()
    )

    private fun Product.calculateFinalPrice(): Double {
        val vatRate = VatConfig.getVatRate(country)
        // discounts compound, they don't add up - 10% then 20% off leaves 0.9 * 0.8 = 72% of
        // the price, not 1 - (0.10 + 0.20) = 70%
        val priceAfterDiscounts = discounts.fold(basePrice) { price, discount ->
            price * (1 - discount.percent / 100.0)
        }
        return priceAfterDiscounts * (1 + vatRate)
    }
}
