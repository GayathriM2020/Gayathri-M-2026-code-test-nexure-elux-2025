package io.nexure.discount.model

import kotlinx.serialization.Serializable

@Serializable
data class Discount(
    val discountId: String,
    val percent: Double
)
