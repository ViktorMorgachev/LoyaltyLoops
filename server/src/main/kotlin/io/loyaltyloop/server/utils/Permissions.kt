package io.loyaltyloop.server.utils

import io.loyaltyloop.server.repository.PartnerRepository
import io.loyaltyloop.shared.models.AppErrorCode

suspend fun requirePartnerWriteAccess(repo: PartnerRepository, userId: String, partnerId: String) {
    val partner = repo.getPartnerById(partnerId)
    
    // Проверяем, что userId - это владелец бизнеса
    if (partner.ownerId != userId) {
        throw LoyaltyException(AppErrorCode.FORBIDDEN, "Only owner can modify business")
    }
}

suspend fun requirePointReadAccess(repo: PartnerRepository, userId: String, pointId: String) {
    val partnerId = repo.getPartnerIdByPoint(pointId)
    
    val partner = repo.getPartnerById(partnerId)
    
    if (partner.ownerId == userId) return
    if (repo.isUserManager(userId, partnerId)) return
    
    throw LoyaltyException(AppErrorCode.FORBIDDEN, "Access denied")
}

suspend fun requirePointWriteAccess(repo: PartnerRepository, userId: String, pointId: String) {
    val partnerId = repo.getPartnerIdByPoint(pointId)
    requirePartnerWriteAccess(repo, userId, partnerId)
}

