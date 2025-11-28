package io.loyaltyloop.app.utils

import io.loyaltyloop.shared.models.TransactionResult
import io.loyaltyloop.shared.models.TransactionSuccessType
import loyaltyloop.composeapp.generated.resources.*

object TransactionResultMapper {
    fun getMessage(result: TransactionResult): UiText {
        return when (result.type) {
            TransactionSuccessType.POINTS_EARNED -> {
                val earned = result.args.getOrNull(0) ?: "0"
                UiText.concat(
                    UiText.Resource(Res.string.msg_points_earned),
                    UiText.DynamicString(earned),
                    UiText.Resource(Res.string.msg_points_suffix)
                )
            }
            TransactionSuccessType.POINTS_SPENT -> {
                val spent = result.args.getOrNull(0) ?: "0"
                UiText.concat(
                    UiText.Resource(Res.string.msg_points_spent),
                    UiText.DynamicString(spent),
                    UiText.Resource(Res.string.msg_points_suffix)
                )
            }
            TransactionSuccessType.POINTS_SPENT_EARNED -> {
                val spent = result.args.getOrNull(0) ?: "0"
                val earned = result.args.getOrNull(1) ?: "0"
                UiText.concat(
                    UiText.Resource(Res.string.msg_points_spent_earned),
                    UiText.DynamicString(spent),
                    UiText.Resource(Res.string.msg_points_suffix),
                    UiText.Resource(Res.string.msg_points_spent_earned_middle),
                    UiText.DynamicString(earned),
                    UiText.Resource(Res.string.msg_points_suffix)
                )
            }
            TransactionSuccessType.VISIT_PROGRESS -> {
                val current = result.args.getOrNull(0) ?: "0"
                val target = result.args.getOrNull(1) ?: "?"
                UiText.concat(
                    UiText.Resource(Res.string.msg_visit_progress),
                    UiText.DynamicString(current),
                    UiText.Resource(Res.string.msg_visit_progress_suffix),
                    UiText.DynamicString(target)
                )
            }
            TransactionSuccessType.VISIT_REWARD -> {
                val target = result.args.getOrNull(0) ?: "?"
                UiText.concat(
                    UiText.Resource(Res.string.msg_visit_reward),
                    UiText.DynamicString(target),
                    UiText.Resource(Res.string.msg_visit_reward_suffix)
                )
            }
            TransactionSuccessType.BALANCE_INFO -> {
                val balance = result.args.getOrNull(0) ?: "0"
                UiText.concat(
                    UiText.Resource(Res.string.msg_balance_info),
                    UiText.DynamicString(balance)
                )
            }
            else -> {
                // Output: "Операция успешна!"
                UiText.Resource(Res.string.msg_success_default)
            }
        }
    }
}

