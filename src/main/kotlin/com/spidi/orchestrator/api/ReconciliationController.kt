package com.spidi.orchestrator.api

import com.spidi.orchestrator.api.dto.BankNotificationRequest
import com.spidi.orchestrator.core.ReconciliationEngine
import com.spidi.orchestrator.core.ReconciliationResult
import com.spidi.orchestrator.domain.BankNotification
import com.spidi.orchestrator.repository.BankNotificationRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/bank-notifications")
@Tag(name = "Reconciliation", description = "Motor de conciliación bancaria en tiempo real (eliminación de captures)")
class ReconciliationController(
    private val reconciliationEngine: ReconciliationEngine,
    private val bankNotificationRepository: BankNotificationRepository
) {

    @PostMapping
    @Operation(
        summary = "Recibir notificación bancaria (Feed de Banco Aliado)",
        description = "Endpoint utilizado por los bancos o bots de extractos para notificar transferencias Pago Móvil recibidas. El motor concilia inmediatamente pagos en estado PENDING sin intervención humana."
    )
    fun receiveBankNotification(
        @Valid @RequestBody req: BankNotificationRequest
    ): ResponseEntity<ReconciliationResult> {
        val notification = BankNotification(
            bankCode = req.bankCode,
            reference = req.reference,
            amount = req.amount,
            senderPhone = req.senderPhone,
            senderNationalId = req.senderNationalId
        )

        val result = reconciliationEngine.processBankNotification(notification)
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @GetMapping
    @Operation(summary = "Listar notificaciones bancarias recibidas", description = "Muestra el libro de transferencias bancarias y su estado de conciliación.")
    fun listNotifications(): ResponseEntity<List<BankNotification>> {
        return ResponseEntity.ok(bankNotificationRepository.findAll())
    }
}
