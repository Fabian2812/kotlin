package com.consultainventario.app

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

/** Producto o pedido listo para mostrar en una lista y buscar por su contenido. */
internal data class ApiRecord(
    val data: JSONObject,
    val id: String?,
    val title: String,
    val summary: String,
    val searchText: String
)

/** Una línea de pedido con la cantidad solicitada de ese producto. */
internal data class OrderItem(
    val name: String,
    val quantity: Int,
    val unitPrice: String,
    val lineTotal: String
)

/** Adapta las respuestas JSON del servicio a elementos legibles para la interfaz. */
internal object ApiRecords {
    fun list(body: String, route: Route): List<ApiRecord>? {
        val root = JSONTokener(body).nextValue()
        val array = findArray(root, route, 0) ?: return null
        return (0 until array.length()).mapNotNull { index ->
            (array.opt(index) as? JSONObject)?.let { fromObject(it, route) }
        }
    }

    fun detail(body: String, route: Route): JSONObject? {
        val root = JSONTokener(body).nextValue() as? JSONObject ?: return null
        val names = if (route == Route.PRODUCT) listOf("product", "producto", "data")
            else listOf("order", "pedido", "data")
        for (name in names) {
            root.optJSONObject(name)?.let { nested ->
                return if (name == "data") detailInside(nested, route) else nested
            }
        }
        return root
    }

    fun fields(data: JSONObject, excluded: Set<String> = emptySet()): List<Pair<String, String>> =
        data.keys().asSequence().filterNot { it in excluded }.mapNotNull { key ->
        val value = data.opt(key)
        if (value == null || value == JSONObject.NULL) null
        else readableName(key) to when (value) {
            is JSONArray -> "${value.length()} elemento(s)"
            is JSONObject -> value.toString(2)
            else -> value.toString()
        }
    }.toList()

    /** Extrae las cantidades del detalle de un pedido; el listado solo trae item_count. */
    fun orderItems(data: JSONObject): List<OrderItem> {
        val items = data.optJSONArray("items") ?: return emptyList()
        return (0 until items.length()).mapNotNull { index ->
            val item = items.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("product_id")
            OrderItem(
                name = item.optString("product_name").takeIf { it.isNotBlank() } ?: "Producto #$id",
                quantity = item.optInt("quantity", 0),
                unitPrice = item.optString("unit_price"),
                lineTotal = item.optString("line_total")
            )
        }
    }

    private fun fromObject(data: JSONObject, route: Route): ApiRecord {
        val id = first(data, if (route == Route.PRODUCTS) listOf("id", "product_id", "producto_id")
            else listOf("id", "order_id", "pedido_id"))
        val title = if (route == Route.PRODUCTS) {
            first(data, listOf("name", "nombre", "title", "product_name", "sku"))
                ?: "Producto${id?.let { " #$it" } ?: ""}"
        } else {
            "Pedido${id?.let { " #$it" } ?: ""}"
        }
        val summaryKeys = if (route == Route.PRODUCTS) listOf("sku", "price", "precio", "stock", "quantity", "cantidad")
            else listOf("status", "estado", "item_count", "total", "created_at", "fecha", "date")
        val summary = summaryKeys.mapNotNull { key ->
            first(data, listOf(key))?.let { "${readableName(key)}: $it" }
        }.take(3).joinToString("  ·  ")
        return ApiRecord(data, id, title, summary, data.toString().lowercase())
    }

    private fun first(data: JSONObject, names: List<String>): String? = names.firstNotNullOfOrNull { name ->
        data.opt(name)?.takeUnless { it == JSONObject.NULL || it is JSONObject || it is JSONArray }
            ?.toString()?.trim()?.takeIf { it.isNotEmpty() }
    }

    private fun findArray(value: Any?, route: Route, depth: Int): JSONArray? {
        if (depth > 3) return null
        if (value is JSONArray) {
            return if (value.length() == 0 || (0 until value.length()).any { value.opt(it) is JSONObject }) value else null
        }
        if (value !is JSONObject) return null
        val names = if (route == Route.PRODUCTS) listOf("products", "productos", "data", "items", "results", "records")
            else listOf("orders", "pedidos", "data", "items", "results", "records")
        for (name in names) {
            findArray(value.opt(name), route, depth + 1)?.let { return it }
        }
        for (name in value.keys()) {
            if (name !in names) findArray(value.opt(name), route, depth + 1)?.let { return it }
        }
        return null
    }

    private fun detailInside(data: JSONObject, route: Route): JSONObject {
        val name = if (route == Route.PRODUCT) "product" else "order"
        return data.optJSONObject(name) ?: data
    }

    private fun readableName(key: String): String = when (key.lowercase()) {
        "id", "product_id", "order_id" -> "ID"
        "name", "nombre", "product_name" -> "Nombre"
        "sku" -> "SKU"
        "price", "precio" -> "Precio"
        "stock", "quantity", "cantidad" -> "Existencias"
        "status", "estado" -> "Estado"
        "customer_name" -> "Cliente"
        "notes" -> "Notas"
        "item_count" -> "Productos diferentes"
        "total" -> "Total"
        "created_at", "fecha", "date" -> "Fecha"
        else -> key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
