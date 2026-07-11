package com.bote.app.config

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.appcompat.app.AppCompatDelegate
import com.bote.app.R

/** Tema de la app: blanco (claro), negro (oscuro) o el que dicte el sistema. */
enum class Tema(@StringRes val nombreRes: Int, val modoNoche: Int) {
    CLARO(R.string.tema_blanco, AppCompatDelegate.MODE_NIGHT_NO),
    OSCURO(R.string.tema_negro, AppCompatDelegate.MODE_NIGHT_YES),
    SISTEMA(R.string.tema_sistema, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);

    companion object {
        fun fromNombre(nombre: String?): Tema =
            entries.firstOrNull { it.name == nombre } ?: SISTEMA
    }
}

/**
 * Paleta de colores elegible en Ajustes, tanto para el color principal
 * (toolbar, botón de guardar) como para el de acento (FAB, controles).
 * Cada entrada lleva sus dos overlays de tema (ver themes.xml).
 */
enum class PaletaColor(
    @StringRes val nombreRes: Int,
    @ColorRes val colorRes: Int,
    @StyleRes val overlayPrimario: Int,
    @StyleRes val overlayAcento: Int
) {
    NEGRO(R.string.color_negro, R.color.paleta_negro, R.style.Overlay_Bote_Primario_Negro, R.style.Overlay_Bote_Acento_Negro),
    ROJO(R.string.color_rojo, R.color.paleta_rojo, R.style.Overlay_Bote_Primario_Rojo, R.style.Overlay_Bote_Acento_Rojo),
    AZUL(R.string.color_azul, R.color.paleta_azul, R.style.Overlay_Bote_Primario_Azul, R.style.Overlay_Bote_Acento_Azul),
    VERDE(R.string.color_verde, R.color.paleta_verde, R.style.Overlay_Bote_Primario_Verde, R.style.Overlay_Bote_Acento_Verde),
    MORADO(R.string.color_morado, R.color.paleta_morado, R.style.Overlay_Bote_Primario_Morado, R.style.Overlay_Bote_Acento_Morado),
    TEAL(R.string.color_teal, R.color.paleta_teal, R.style.Overlay_Bote_Primario_Teal, R.style.Overlay_Bote_Acento_Teal),
    NARANJA(R.string.color_naranja, R.color.paleta_naranja, R.style.Overlay_Bote_Primario_Naranja, R.style.Overlay_Bote_Acento_Naranja),
    ROSA(R.string.color_rosa, R.color.paleta_rosa, R.style.Overlay_Bote_Primario_Rosa, R.style.Overlay_Bote_Acento_Rosa);

    companion object {
        fun fromNombre(nombre: String?, porDefecto: PaletaColor): PaletaColor =
            entries.firstOrNull { it.name == nombre } ?: porDefecto
    }
}

/** Preferencias persistentes: apariencia (tema y colores) y notificaciones. */
object Ajustes {

    private const val PREFS = "bote_ajustes"
    private const val CLAVE_TEMA = "tema"
    private const val CLAVE_COLOR_PRIMARIO = "color_primario"
    private const val CLAVE_COLOR_ACENTO = "color_acento"
    private const val CLAVE_NOTIF_EVENTO = "notif_evento"
    private const val CLAVE_NOTIF_CIERRE = "notif_cierre"
    private const val CLAVE_NOTIF_PAGOS = "notif_pagos"
    private const val CLAVE_COBRO_ACTIVO = "cobro_activo"
    private const val CLAVE_COBRO_PLANTILLA = "cobro_plantilla"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun tema(context: Context): Tema =
        Tema.fromNombre(prefs(context).getString(CLAVE_TEMA, null))

    fun guardarTema(context: Context, tema: Tema) {
        prefs(context).edit().putString(CLAVE_TEMA, tema.name).apply()
        AppCompatDelegate.setDefaultNightMode(tema.modoNoche)
    }

    /** Aplica el modo noche guardado; llamar al arrancar la app. */
    fun aplicarTema(context: Context) {
        AppCompatDelegate.setDefaultNightMode(tema(context).modoNoche)
    }

    fun colorPrimario(context: Context): PaletaColor =
        PaletaColor.fromNombre(
            prefs(context).getString(CLAVE_COLOR_PRIMARIO, null), PaletaColor.NEGRO
        )

    fun guardarColorPrimario(context: Context, color: PaletaColor) {
        prefs(context).edit().putString(CLAVE_COLOR_PRIMARIO, color.name).apply()
    }

    fun colorAcento(context: Context): PaletaColor =
        PaletaColor.fromNombre(
            prefs(context).getString(CLAVE_COLOR_ACENTO, null), PaletaColor.ROJO
        )

    fun guardarColorAcento(context: Context, color: PaletaColor) {
        prefs(context).edit().putString(CLAVE_COLOR_ACENTO, color.name).apply()
    }

    /** Huella de los colores elegidos, para detectar cambios y recrear actividades. */
    fun firmaColores(context: Context): String =
        "${colorPrimario(context).name}/${colorAcento(context).name}"

    // ── Identidad del usuario ─────────────────────────────────────

    private const val CLAVE_NOMBRE_USUARIO = "nombre_usuario"

    /** Tu nombre; se usa como asistente creador al crear un evento. */
    fun nombreUsuario(context: Context): String =
        prefs(context).getString(CLAVE_NOMBRE_USUARIO, "").orEmpty()

    fun guardarNombreUsuario(context: Context, valor: String) {
        prefs(context).edit().putString(CLAVE_NOMBRE_USUARIO, valor.trim()).apply()
    }

    private const val CLAVE_MIGRACION_NOMBRE = "migracion_nombre_hecha"

    /** True si ya se renombraron los creadores antiguos ("Yo"/vacíos). */
    fun migracionNombreHecha(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_MIGRACION_NOMBRE, false)

    fun marcarMigracionNombreHecha(context: Context) {
        prefs(context).edit().putBoolean(CLAVE_MIGRACION_NOMBRE, true).apply()
    }

    // ── Notificaciones ────────────────────────────────────────────

    fun notifEvento(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_NOTIF_EVENTO, true)

    fun guardarNotifEvento(context: Context, valor: Boolean) {
        prefs(context).edit().putBoolean(CLAVE_NOTIF_EVENTO, valor).apply()
    }

    fun notifCierre(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_NOTIF_CIERRE, true)

    fun guardarNotifCierre(context: Context, valor: Boolean) {
        prefs(context).edit().putBoolean(CLAVE_NOTIF_CIERRE, valor).apply()
    }

    fun notifPagos(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_NOTIF_PAGOS, true)

    fun guardarNotifPagos(context: Context, valor: Boolean) {
        prefs(context).edit().putBoolean(CLAVE_NOTIF_PAGOS, valor).apply()
    }

    // ── Mensajes de cobro (desactivados por defecto) ──────────────

    fun cobroActivo(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_COBRO_ACTIVO, false)

    fun guardarCobroActivo(context: Context, valor: Boolean) {
        prefs(context).edit().putBoolean(CLAVE_COBRO_ACTIVO, valor).apply()
    }

    /** Plantilla editable; admite {nombre}, {importe} y {evento}. */
    fun cobroPlantilla(context: Context): String =
        prefs(context).getString(CLAVE_COBRO_PLANTILLA, null)
            ?: context.getString(R.string.cobro_plantilla_defecto)

    fun guardarCobroPlantilla(context: Context, valor: String) {
        prefs(context).edit().putString(CLAVE_COBRO_PLANTILLA, valor).apply()
    }

    // ── Orden de la lista de eventos ─────────────────────────────

    private const val CLAVE_ORDEN_EVENTOS = "orden_eventos"

    /** Devuelve la clave de orden guardada (FECHA por defecto). */
    fun ordenEventos(context: Context): String =
        prefs(context).getString(CLAVE_ORDEN_EVENTOS, "FECHA") ?: "FECHA"

    fun guardarOrdenEventos(context: Context, clave: String) {
        prefs(context).edit().putString(CLAVE_ORDEN_EVENTOS, clave).apply()
    }

    // ── Detector de pagos (desactivado por defecto) ──────────────

    private const val CLAVE_DETECTOR = "detector_pagos"

    fun detectorActivo(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_DETECTOR, false)

    fun guardarDetectorActivo(context: Context, valor: Boolean) {
        prefs(context).edit().putBoolean(CLAVE_DETECTOR, valor).apply()
    }

    // ── Sincronización en la nube (desactivada por defecto) ──────

    private const val CLAVE_SYNC_ACTIVO = "sync_activo"
    private const val CLAVE_SYNC_URL = "sync_url"
    private const val CLAVE_SYNC_KEY = "sync_key"

    fun syncActivo(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_SYNC_ACTIVO, false)

    fun guardarSyncActivo(context: Context, valor: Boolean) {
        prefs(context).edit().putBoolean(CLAVE_SYNC_ACTIVO, valor).apply()
    }

    fun syncUrl(context: Context): String =
        prefs(context).getString(CLAVE_SYNC_URL, "").orEmpty()

    fun guardarSyncUrl(context: Context, valor: String) {
        prefs(context).edit().putString(CLAVE_SYNC_URL, valor.trim()).apply()
    }

    fun syncKey(context: Context): String =
        prefs(context).getString(CLAVE_SYNC_KEY, "").orEmpty()

    fun guardarSyncKey(context: Context, valor: String) {
        prefs(context).edit().putString(CLAVE_SYNC_KEY, valor.trim()).apply()
    }

    // ── Sincronización en segundo plano ──────────────────────────

    private const val CLAVE_AUTO_SYNC = "auto_sync"
    private const val CLAVE_AUTO_SYNC_WIFI = "auto_sync_wifi"

    /** Sincronizar en segundo plano cada hora (activado por defecto). */
    fun autoSyncActivo(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_AUTO_SYNC, true)

    fun guardarAutoSyncActivo(context: Context, valor: Boolean) {
        prefs(context).edit().putBoolean(CLAVE_AUTO_SYNC, valor).apply()
    }

    /** Limitar la sync en segundo plano a WiFi/red no medida (por defecto sí). */
    fun autoSyncSoloWifi(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_AUTO_SYNC_WIFI, true)

    fun guardarAutoSyncSoloWifi(context: Context, valor: Boolean) {
        prefs(context).edit().putBoolean(CLAVE_AUTO_SYNC_WIFI, valor).apply()
    }

    // ── Última sincronización correcta por evento (local) ─────────

    /** Millis de la última sincronización correcta de un evento (0 si nunca). */
    fun ultimaSync(context: Context, uuid: String): Long =
        prefs(context).getLong("ultima_sync_$uuid", 0L)

    fun guardarUltimaSync(context: Context, uuid: String, millis: Long) {
        prefs(context).edit().putLong("ultima_sync_$uuid", millis).apply()
    }

    // ── Avatar del evento sincronizado (local) ────────────────────

    /** avatarMillis de la imagen que tenemos físicamente en este dispositivo. */
    fun avatarImagenMillis(context: Context, uuid: String): Long =
        prefs(context).getLong("avatar_img_$uuid", 0L)

    fun guardarAvatarImagenMillis(context: Context, uuid: String, millis: Long) {
        prefs(context).edit().putLong("avatar_img_$uuid", millis).apply()
    }

    /** avatarMillis de la imagen que ya hemos subido a Storage. */
    fun avatarSubidoMillis(context: Context, uuid: String): Long =
        prefs(context).getLong("avatar_sub_$uuid", 0L)

    fun guardarAvatarSubidoMillis(context: Context, uuid: String, millis: Long) {
        prefs(context).edit().putLong("avatar_sub_$uuid", millis).apply()
    }

    // ── Suscripción (sabor play) ──────────────────────────────────

    private const val CLAVE_SUSCRIPCION = "suscripcion_activa"

    fun suscripcionActiva(context: Context): Boolean =
        prefs(context).getBoolean(CLAVE_SUSCRIPCION, false)

    fun guardarSuscripcionActiva(context: Context, valor: Boolean) {
        prefs(context).edit().putBoolean(CLAVE_SUSCRIPCION, valor).apply()
    }

    // ── Cifrado extremo a extremo (frase, nunca sale del dispositivo) ──

    private const val CLAVE_FRASE_CIFRADO = "frase_cifrado"

    /** Frase para derivar la clave de cifrado E2E; vacía = sync en claro. */
    fun fraseCifrado(context: Context): String =
        prefs(context).getString(CLAVE_FRASE_CIFRADO, "").orEmpty()

    fun guardarFraseCifrado(context: Context, valor: String) {
        prefs(context).edit().putString(CLAVE_FRASE_CIFRADO, valor).apply()
    }

    // ── Sync diferencial: firma del último blob subido por evento ──

    fun firmaSubida(context: Context, uuid: String): String =
        prefs(context).getString("firma_sub_$uuid", "").orEmpty()

    fun guardarFirmaSubida(context: Context, uuid: String, firma: String) {
        prefs(context).edit().putString("firma_sub_$uuid", firma).apply()
    }

    /**
     * Borra el estado local de sincronización de un evento (al eliminarlo).
     * Evita que las prefs crezcan sin límite y que una reimportación del
     * mismo evento herede firmas o marcas de avatar rancias.
     */
    fun limpiarEstadoSync(context: Context, uuid: String) {
        prefs(context).edit()
            .remove("ultima_sync_$uuid")
            .remove("avatar_img_$uuid")
            .remove("avatar_sub_$uuid")
            .remove("firma_sub_$uuid")
            .apply()
    }
}
