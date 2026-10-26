package br.com.vps.creditservice.resource.model.enums

enum class EHttpHeader(
    val headerName: String,
) {
    API_KEY("X-API-Key"),
    IDEMPOTENCY_KEY("Idempotency-Key"),
    ;

    companion object {
        const val IDEMPOTENCY_KEY_VALUE = "Idempotency-Key"
    }
}
