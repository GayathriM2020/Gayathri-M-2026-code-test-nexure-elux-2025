package io.nexure.discount.config

object VatConfig {
    private const val SWEDEN_VAT = 0.25
    private const val GERMANY_VAT = 0.19
    private const val FRANCE_VAT = 0.20

    private val vatRates = mapOf(
        "Sweden" to SWEDEN_VAT,
        "Germany" to GERMANY_VAT,
        "France" to FRANCE_VAT
    )
    
    fun getVatRate(country: String): Double {
        return vatRates[country] ?: 0.0
    }
}
