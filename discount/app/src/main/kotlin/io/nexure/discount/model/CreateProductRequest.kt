package io.nexure.discount.model

import kotlinx.serialization.Serializable

// body for the POST /products endpoint - added this for manual testing, not part of the
// original spec. new products always start with no discounts, add those via the PUT endpoint
@Serializable
data class CreateProductRequest(
    val id: String,
    val name: String,
    val basePrice: Double,
    val country: String
)
