# Spidi Orchestrator Backend MVP 🚀
### Orquestador de Pagos y Cobranzas Digitales en Kotlin + Spring Boot 3

Plataforma de orquestación de pagos multi-riel inspirada en el modelo de **Spidi** (Fintech venezolana). Diseñada para eliminar el proceso manual de verificación de transferencias y captures de pantalla mediante conciliación en tiempo real, conectando de forma no custodial los rieles de pago más utilizados: **Pago Móvil C2P**, **Pago Móvil P2C con matching automático**, **Criptoactivos (USDT)** y **Tarjetas**.

---

## 🛠️ Stack Tecnológico

- **Lenguaje:** [Kotlin 1.9+](https://kotlinlang.org/) sobre **Java 17 LTS**.
- **Framework:** [Spring Boot 3.3](https://spring.io/projects/spring-boot).
- **Persistencia:** [PostgreSQL 16](https://www.postgresql.org/) + Spring Data JPA / Hibernate (con soporte para H2 en perfiles de desarrollo rápido).
- **Caché y Concurrencia:** [Redis 7](https://redis.io/) con Lettuce para control de **Idempotencia distribuida** (`Idempotency-Key`), bloqueo atómico y caché de tasas de cambio.
- **Documentación de API:** [OpenAPI 3 / Swagger UI](http://localhost:8080/swagger-ui.html) (`springdoc-openapi`).
- **Seguridad y Webhooks:** Firmas criptográficas HMAC SHA-256 (`X-Spidi-Signature`).
- **Contenedores:** Docker & Docker Compose (`docker-compose.yml`).

---

## 🏛️ Arquitectura del Sistema

```
                      +-----------------------------+
                      |       Comercio / App        |
                      |   (POST /api/v1/payments)   |
                      +--------------+--------------+
                                     |
                       (Idempotency-Key / Redis)
                                     v
                  +-----------------------------------+
                  |    SPIDI PAYMENT ORCHESTRATOR     |
                  |     (Core Routing & Lifecycle)    |
                  +--+-------------+---------------+--+
                     |             |               |
                     v             v               v
            +----------------+ +---------------+ +---------------+
            | Pago Móvil C2P | |  Cripto USDT  | | Tarjeta (Card)|
            |  (OTP Débito)  | | (TRON TRC-20) | | (Luhn / Token)|
            +----------------+ +---------------+ +---------------+
                     ^
                     | (Feed de Bancos Aliados / Webhooks)
            +--------+-------------------------+
            |      ReconciliationEngine        |
            | (Matching automático sin captura)|
            +----------------------------------+
```

### Rieles de Pago Soportados

1. **Pago Móvil C2P (Comercio a Persona):** Débito directo en el banco emisor solicitando Banco, Teléfono, Cédula y Token Dinámico (OTP).
2. **Pago Móvil P2C (Persona a Comercio con Conciliación Automática):** El pagador realiza una transferencia a los datos bancarios del comercio. El `ReconciliationEngine` cruza el monto, teléfono, cédula y referencia bancaria en tiempo real para aprobar la orden en milisegundos.
3. **Cripto USDT (Red TRON TRC-20):** Permite cobrar en stablecoins cotizadas a la tasa oficial o monto directo en dólares.
4. **Tarjetas de Débito y Crédito:** Procesamiento estándar con validación de algoritmo de Luhn y tokenización.

---

## 🚀 Inicio Rápido

### 1. Iniciar Infraestructura (PostgreSQL + Redis) con Docker
Si dispones de Docker instalado, levanta los servicios con un solo comando:
```bash
docker compose up -d
```
Esto iniciará:
- **PostgreSQL 16:** Puerto `5432` (Base de datos: `spidi_db`, Usuario: `spidi_user`, Password: `spidi_password`).
- **Redis 7:** Puerto `6379`.
- **Adminer (Gestor Web de DB):** `http://localhost:8081`.

### 2. Ejecutar la Aplicación
```bash
# Ejecución en perfil local (listo para desarrollo inmediato):
mvn spring-boot:run

# O ejecutando con el perfil Docker (PostgreSQL + Redis):
mvn spring-boot:run "-Dspring-boot.run.profiles=docker"
```

---

## 📖 Documentación Interactiva (Swagger UI)

Una vez iniciada la aplicación, accede al playground interactivo de OpenAPI:
👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**
👉 **OpenAPI JSON:** `http://localhost:8080/v3/api-docs`

---

## 🎨 Hosted Checkout Unificado

El orquestador incluye una interfaz de checkout responsiva y moderna construida con Tailwind CSS.

Para visualizar el checkout de un pago:
1. Crea un pago vía API o Swagger (`POST /api/v1/payments`).
2. Abre en tu navegador la URL devuelta en el campo `checkoutUrl`:
   👉 `http://localhost:8080/checkout/{paymentId}`
3. Prueba pagar con C2P ingresando cualquier OTP (o `000000` para simular fondos insuficientes), conciliar con P2C, o probar Cripto/Tarjeta.

---

## 🔐 Autenticación y Credenciales Demo

La aplicación inicializa automáticamente un comercio de pruebas:
- **X-API-KEY:** `spidi_sec_demo_secret_key_99999`
- **Webhook Secret:** `whsec_demo_signature_secret_888`

---

## 🧪 Pruebas Unitarias

Para ejecutar la suite completa de pruebas unitarias:
```bash
mvn test
```

Incluye tests para:
- Conciliación automática en tiempo real de notificaciones bancarias.
- Generación y verificación criptográfica de firmas HMAC SHA-256 de Webhooks.
- Validación de flujo C2P con OTP dinámico y débitos exitosos/fallidos.
- Validación de tarjetas con algoritmo de Luhn.
