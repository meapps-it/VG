package it.meapps.gestionale

/** Predisposizione senza paywall: nella fase 1 tutte le funzioni sono utilizzabili. */
enum class AppPlan { FREE, PREMIUM }

interface EntitlementProvider {
    val currentPlan: AppPlan
    fun allows(feature: PremiumFeature): Boolean
}

enum class PremiumFeature {
    UNLIMITED_PRODUCTS, MULTIPLE_IMAGES, ADVANCED_REPORTS, EXPORTS, CLOUD_BACKUP
}

class PhaseOneEntitlements : EntitlementProvider {
    override val currentPlan = AppPlan.FREE
    override fun allows(feature: PremiumFeature) = true
}
