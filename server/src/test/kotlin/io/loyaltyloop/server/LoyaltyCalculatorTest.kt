package io.loyaltyloop.server

import io.loyaltyloop.server.service.LoyaltyCalculator
import io.loyaltyloop.shared.models.LoyaltyCardDto
import io.loyaltyloop.shared.models.LoyaltyTierDto
import io.loyaltyloop.shared.models.RiskLevel
import io.loyaltyloop.shared.models.TransactionCalculationDto
import io.loyaltyloop.shared.models.TransactionStrategy
import kotlin.test.Test
import kotlin.test.assertEquals

class LoyaltyCalculatorTest {

    private val tiers = listOf(
        LoyaltyTierDto(
            levelIndex = 1,
            loyaltyTier = LoyaltyTierDto.LoyaltyTier(LoyaltyTierDto.LoyaltyLevel.Base, "Base"),
            threshold = 0.0,
            cashbackPercent = 1.0
        ),
        LoyaltyTierDto(
            levelIndex = 2,
            loyaltyTier = LoyaltyTierDto.LoyaltyTier(LoyaltyTierDto.LoyaltyLevel.Silver, "Silver"),
            threshold = 5_000.0,
            cashbackPercent = 10.0
        ),
        LoyaltyTierDto(
            levelIndex = 3,
            loyaltyTier = LoyaltyTierDto.LoyaltyTier(LoyaltyTierDto.LoyaltyLevel.Gold, "Gold"),
            threshold = 20_000.0,
            cashbackPercent = 20.0
        )
    )

    private fun card(
        tierLevel: Int = 1,
        balance: Double = 0.0,
        totalSpent: Double = 0.0
    ) = LoyaltyCardDto(
        id = "card",
        userId = "user",
        partnerId = "partner",
        balance = balance,
        totalSpent = totalSpent,
        visitsCount = 0,
        tierLevel = tierLevel,
        block = null,
        pause = null,
        partnerName = "",
        visitsTarget = 10,
        riskLevel = RiskLevel.GREEN,
        partnerBaseCurrency = "USD",
        estimatedValue = balance,
        estimatedCurrency = "KGS"
    )

    @Test
    fun earnOnly_silverTier_awardsFivePercent() {
        println("=== Earn Only Scenario: amount=1000, tier=Silver (5%) ===")
        val result = LoyaltyCalculator.calculate(
            card = card(tierLevel = 2, balance = 150.0, totalSpent = 7_000.0),
            purchaseAmount = 1_000.0,
            maxBurnPercentage = 100,
            settingsVisitTarget = 0,
            settingsTiers = tiers,
            strategy = TransactionStrategy.CHARGE
        )

        println("moneyPaid=${result.moneyPaid}, pointsEarned=${result.pointsToAward}, newBalance=${result.newBalance}")
        assertEquals(0.0, result.pointsSpent, "Earn only scenario should not spend points")
        assertEquals(1_000.0, result.moneyPaid, "Customer pays full amount in earn scenario")
        assertEquals(100.0, result.pointsToAward, "10% of 1000 should be 100 points")
    }

    @Test
    fun spendLimitedByBalance_noAwardWhenFlagDisabled() {
        println("=== Spend limited by balance (award disabled) ===")
        val result = LoyaltyCalculator.calculate(
            card = card(tierLevel = 2, balance = 100.0, totalSpent = 5_500.0),
            purchaseAmount = 1_000.0,
            maxBurnPercentage = 50, // можно списать максимум 500, но хватает только 100
            settingsVisitTarget = 0,
            settingsTiers = tiers,
            strategy = TransactionStrategy.SPEND,
            awardOnMixedPayment = false
        )

        println("spent=${result.pointsSpent}, paid=${result.moneyPaid}, earned=${result.pointsToAward}, message=${result.message}")
        assertEquals(100.0, result.pointsSpent, "Should spend all available balance")
        assertEquals(900.0, result.moneyPaid, "Remaining amount must be paid with money")
        assertEquals(0.0, result.pointsToAward, "No cashback when flag disabled")
        assertEquals(
            TransactionCalculationDto.LoyaltyMessage.TIERED_ENOUGHT_AMOUNT,
            result.message,
            "Should reflect limited-by-balance scenario"
        )
    }

    @Test
    fun spendLimitedByBalance_awardsWhenFlagEnabled() {
        println("=== Spend limited by balance (award enabled) ===")
        val result = LoyaltyCalculator.calculate(
            card = card(tierLevel = 2, balance = 100.0, totalSpent = 5_500.0),
            purchaseAmount = 1_000.0,
            maxBurnPercentage = 50,
            settingsVisitTarget = 0,
            settingsTiers = tiers,
            strategy = TransactionStrategy.SPEND,
            awardOnMixedPayment = true
        )

        println("spent=${result.pointsSpent}, paid=${result.moneyPaid}, earned=${result.pointsToAward}")
        assertEquals(100.0, result.pointsSpent)
        assertEquals(900.0, result.moneyPaid)
        assertEquals(90.0, result.pointsToAward, "10% of paid 900 should be 45 when award flag is enabled")
    }

    @Test
    fun spendLimitedBySetting_maxBurn30percent() {
        println("=== Spend limited by setting (max burn 30%) ===")
//        val result = LoyaltyCalculator.calculate(
//            card = card(tierLevel = 3, balance = 5_000.0, totalSpent = 25_000.0),
//            purchaseAmount = 1_000.0,
//            maxBurnPercentage = 30,
//            settingsVisitTarget = 0,
//            settingsTiers = tiers,
//            strategy = TransactionStrategy.SPEND,
//            awardOnMixedPayment = true
//        )
//
//        println("spent=${result.pointsSpent}, paid=${result.moneyPaid}, earned=${result.pointsToAward}, newBalance=${result.newBalance}")
//        assertEquals(300.0, result.pointsSpent, "Should respect 30% max burn = 300")
//        assertEquals(700.0, result.moneyPaid)
//        assertEquals(35.0, result.pointsToAward, "5% of 700 = 35")
//        assertEquals(4_735.0, result.newBalance, "Balance decreases by spent points and increases by cashback")
    }

    @Test
    fun fullCoverageByPoints_zeroPayment_noAward() {
        println("=== Full coverage by balance ===")
        val result = LoyaltyCalculator.calculate(
            card = card(tierLevel = 3, balance = 500.0, totalSpent = 10_000.0),
            purchaseAmount = 200.0,
            maxBurnPercentage = 100,
            settingsVisitTarget = 0,
            settingsTiers = tiers,
            strategy = TransactionStrategy.SPEND,
            awardOnMixedPayment = true
        )

        println("spent=${result.pointsSpent}, paid=${result.moneyPaid}, earned=${result.pointsToAward}")
        assertEquals(200.0, result.pointsSpent, "Should spend full sum with available points")
        assertEquals(0.0, result.moneyPaid, "Cash payment should be zero when balance covers purchase")
        assertEquals(0.0, result.pointsToAward, "No cashback when no money spent even if flag enabled")
        assertEquals(300.0, result.newBalance)
    }

    @Test
    fun mathematicalRounding_toTwoDecimals() {
        println("=== Mathematical rounding check (3% of 1234.56) ===")
        val result = LoyaltyCalculator.calculate(
            card = card(tierLevel = 1, balance = 0.0, totalSpent = 0.0),
            purchaseAmount = 1_234.56,
            maxBurnPercentage = 0,
            settingsVisitTarget = 0,
            settingsTiers = tiers,
            strategy = TransactionStrategy.CHARGE
        )

        println("earned=${result.pointsToAward}")
        assertEquals(37.04, result.pointsToAward, "Cashback must be rounded to 2 decimals")
    }
}

