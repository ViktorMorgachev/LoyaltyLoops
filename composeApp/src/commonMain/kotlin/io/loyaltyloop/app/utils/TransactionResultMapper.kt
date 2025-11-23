package io.loyaltyloop.app.utils

import io.loyaltyloop.shared.models.TransactionResult
import io.loyaltyloop.shared.models.TransactionSuccessType
import loyaltyloop.composeapp.generated.resources.*

object TransactionResultMapper {
    fun getMessage(result: TransactionResult): UiText {
        return when (result.type) {
            TransactionSuccessType.POINTS_EARNED -> {
                val earned = result.args.getOrNull(0) ?: "0"
                // Output example: "Начислено: 50 Б"
                UiText.Resource(Res.string.msg_points_earned, earned)
            }
            TransactionSuccessType.POINTS_SPENT -> {
                val spent = result.args.getOrNull(0) ?: "0"
                // Output example: "Списано: 100 Б"
                UiText.Resource(Res.string.msg_points_spent, spent)
            }
            TransactionSuccessType.POINTS_SPENT_EARNED -> {
                val spent = result.args.getOrNull(0) ?: "0"
                val earned = result.args.getOrNull(1) ?: "0"
                // Output example: "Списано: 100, Начислено: 25"
                UiText.Resource(Res.string.msg_points_spent_earned, spent, earned)
            }
            TransactionSuccessType.VISIT_PROGRESS -> {
                val current = result.args.getOrNull(0) ?: "0"
                val target = result.args.getOrNull(1) ?: "?"
                // Output example: "Визит учтен! Всего: 3 из 6"
                UiText.Resource(Res.string.msg_visit_progress, current, target)
            }
            TransactionSuccessType.VISIT_REWARD -> {
                val target = result.args.getOrNull(0) ?: "?"
                // Output example: "Цель достигнута! Подарок доступен! (6)"
                UiText.Resource(Res.string.msg_visit_reward, target)
            }
            TransactionSuccessType.BALANCE_INFO -> {
                val balance = result.args.getOrNull(0) ?: "0"
                // Output example: "Баланс: 1500"
                UiText.Resource(Res.string.msg_balance_info, balance)
            }
            else -> {
                // Output: "Операция успешна!"
                UiText.Resource(Res.string.msg_success_default)
            }
        }
    }
}

