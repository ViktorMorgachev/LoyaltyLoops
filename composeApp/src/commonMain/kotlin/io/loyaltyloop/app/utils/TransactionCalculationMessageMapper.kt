package io.loyaltyloop.app.utils

import io.loyaltyloop.shared.models.TransactionCalculationDto
import loyaltyloop.composeapp.generated.resources.Res
import loyaltyloop.composeapp.generated.resources.term_calc_msg_charge
import loyaltyloop.composeapp.generated.resources.term_calc_msg_charge_upgrade
import loyaltyloop.composeapp.generated.resources.term_calc_msg_error
import loyaltyloop.composeapp.generated.resources.term_calc_msg_next_reward
import loyaltyloop.composeapp.generated.resources.term_calc_msg_spend
import loyaltyloop.composeapp.generated.resources.term_calc_msg_spend_limit_balance
import loyaltyloop.composeapp.generated.resources.term_calc_msg_visit
import loyaltyloop.composeapp.generated.resources.term_calc_msg_visit_reward

object TransactionCalculationMessageMapper {

    fun map(message: TransactionCalculationDto.LoyaltyMessage): UiText {
        return when (message) {
            TransactionCalculationDto.LoyaltyMessage.VISIT -> UiText.Resource(Res.string.term_calc_msg_visit)
            TransactionCalculationDto.LoyaltyMessage.VISIT_REWARD -> UiText.Resource(Res.string.term_calc_msg_visit_reward)
            TransactionCalculationDto.LoyaltyMessage.NEXT_REWARD -> UiText.Resource(Res.string.term_calc_msg_next_reward)
            TransactionCalculationDto.LoyaltyMessage.TIERED_ENOUGHT_AMOUNT -> UiText.Resource(Res.string.term_calc_msg_spend_limit_balance)
            TransactionCalculationDto.LoyaltyMessage.TIERED_ERROR_AMOUNT -> UiText.Resource(Res.string.term_calc_msg_error)
            TransactionCalculationDto.LoyaltyMessage.TIERED_CHARGE -> UiText.Resource(Res.string.term_calc_msg_charge)
            TransactionCalculationDto.LoyaltyMessage.TIERED_CHARGE_CHANGE_TIER -> UiText.Resource(Res.string.term_calc_msg_charge_upgrade)
            TransactionCalculationDto.LoyaltyMessage.TIERED_SPEND -> UiText.Resource(Res.string.term_calc_msg_spend)
        }
    }
}

