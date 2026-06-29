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

    // used to just return 0.0 for unknown countries, which quietly means "never charge VAT for
    // this market" - that's a compliance problem, not a sane default, so now we just reject it
    fun getVatRate(country: String): Double {
        return vatRates[country]
            ?: throw io.nexure.discount.service.UnsupportedCountryException(country)
    }

    fun isSupported(country: String): Boolean = vatRates.containsKey(country)
}
