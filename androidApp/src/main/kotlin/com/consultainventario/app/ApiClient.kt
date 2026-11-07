package com.consultainventario.app

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Respuesta de la API. El código 0 representa un error local o de conexión. */
internal data class ApiResult(val status: Int, val body: String)

/**
 * Capa de comunicación con la API externa. Construye la URL, agrega la API key
 * cuando corresponde y devuelve el código HTTP y el cuerpo de la respuesta.
 * MainActivity llama a esta clase desde un hilo secundario.
 */
internal class ApiClient {
    private val baseUrl = "https://darkred-rook-511555.hostingersite.com/api/v1"

    /**
     * Ejecuta una de las operaciones permitidas por [Route].
     * [id] se usa en consultas individuales y [json] al crear pedidos.
     * La ruta de estado es pública; las demás requieren [key].
     */
    fun request(route: Route, key: String?, id: String = "", json: String = ""): ApiResult {
        if (route != Route.HEALTH && key.isNullOrBlank()) {
            return ApiResult(0, "Primero guardá tu API key en Configuración.")
        }
        val suffix = when (route) {
            Route.PRODUCT, Route.ORDER -> "?id=${URLEncoder.encode(id, "UTF-8")}" 
            else -> ""
        }
        val connection = (URL("$baseUrl/${route.path}$suffix").openConnection() as HttpURLConnection)
        try {
            // Solo crear pedidos usa POST; todas las consultas son GET.
            connection.requestMethod = if (route == Route.CREATE_ORDER) "POST" else "GET"
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Accept", "application/json")
            if (route != Route.HEALTH) connection.setRequestProperty("X-API-Key", key)
            if (route == Route.CREATE_ORDER) {
                // El cuerpo se envía como JSON; la API decide si el pedido es válido.
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(json.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "(respuesta vacía)"
            return ApiResult(status, body)
        } finally {
            // Libera la conexión tanto si la API responde como si ocurre una excepción.
            connection.disconnect()
        }
    }
}

/** Endpoints disponibles. Se mantiene la lista cerrada para no construir rutas arbitrarias. */
internal enum class Route(val path: String, val title: String) {
    HEALTH("health.php", "Estado del servicio"),
    PRODUCTS("products.php", "Listar productos"),
    PRODUCT("product.php", "Producto por ID"),
    ORDERS("orders.php", "Listar pedidos"),
    ORDER("order.php", "Pedido por ID"),
    CREATE_ORDER("orders.php", "Crear pedido")
}
