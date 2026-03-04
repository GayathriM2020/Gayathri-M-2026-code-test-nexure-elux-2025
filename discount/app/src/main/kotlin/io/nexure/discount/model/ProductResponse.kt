package io.nexure.discount.model

import kotlinx.serialization.Serializable

@Serializable
data class ProductResponse(
    val id: String,
    val name: String,
    val basePrice: Double,
    val country: String,
    val discounts: List<DiscountResponse>,
    val finalPrice: Double
)
