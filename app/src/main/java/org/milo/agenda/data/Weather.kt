package org.milo.agenda.data

import android.content.Context
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.cert.CertificateFactory
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

data class Hour(val time: LocalDateTime, val temp: Double, val pop: Int, val code: Int)
data class Day(val date: LocalDate, val code: Int, val max: Double, val min: Double, val sunrise: LocalTime?, val sunset: LocalTime?, val uv: Double)
data class Weather(
    val temp: Double, val feels: Double, val humidity: Int, val wind: Double, val windDir: Int,
    val pressure: Double, val code: Int, val isDay: Boolean,
    val hours: List<Hour>, val days: List<Day>, val fetched: Long,
)
data class Place(val name: String, val detail: String, val lat: Double, val lon: Double)

/** Clima de Open-Meteo.com: libre, sin clave ni cuenta. */
object WeatherRepo {
    var data by mutableStateOf<Weather?>(null); private set
    var loading by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set

    private var cacheFile: File? = null

    fun init(ctx: Context) {
        if (cacheFile != null) return
        cacheFile = File(ctx.applicationContext.filesDir, "weather.json")
        cacheFile?.takeIf { it.exists() }?.let { f -> runCatching { data = parse(f.readText(), f.lastModified()) } }
    }

    fun isStale() = data == null || System.currentTimeMillis() - (data?.fetched ?: 0) > 30 * 60_000

    fun hourAt(t: LocalDateTime): Hour? = data?.hours?.firstOrNull {
        it.time.toLocalDate() == t.toLocalDate() && it.time.hour == t.hour
    }

    fun day(d: LocalDate): Day? = data?.days?.firstOrNull { it.date == d }

    suspend fun refresh(ctx: Context) {
        if (loading) return
        loading = true; error = null
        val p = Store.prefs
        try {
            val url = "https://api.open-meteo.com/v1/forecast?latitude=${p.lat}&longitude=${p.lon}" +
                "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,wind_direction_10m,surface_pressure,is_day" +
                "&hourly=temperature_2m,precipitation_probability,weather_code" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max" +
                "&timezone=auto&forecast_days=7"
            val text = withContext(Dispatchers.IO) { get(ctx, url) }
            val now = System.currentTimeMillis()
            data = parse(text, now)
            withContext(Dispatchers.IO) { cacheFile?.writeText(text) }
        } catch (e: Exception) {
            error = "No se pudo actualizar el clima. Revisa la conexión."
        } finally {
            loading = false
        }
    }

    suspend fun search(ctx: Context, q: String): List<Place> = withContext(Dispatchers.IO) {
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=8&language=es&name=" + URLEncoder.encode(q, "UTF-8")
        val o = JSONObject(get(ctx, url))
        val a = o.optJSONArray("results") ?: return@withContext emptyList()
        (0 until a.length()).map { i ->
            val r = a.getJSONObject(i)
            Place(
                r.optString("name"),
                listOf(r.optString("admin1"), r.optString("country")).filter { it.isNotBlank() }.joinToString(", "),
                r.getDouble("latitude"), r.getDouble("longitude"),
            )
        }
    }

    private fun parse(text: String, fetched: Long): Weather {
        val o = JSONObject(text)
        val c = o.getJSONObject("current")
        val h = o.getJSONObject("hourly")
        val ht = h.getJSONArray("time"); val htemp = h.getJSONArray("temperature_2m")
        val hpop = h.getJSONArray("precipitation_probability"); val hcode = h.getJSONArray("weather_code")
        val hours = (0 until ht.length()).map { i ->
            Hour(LocalDateTime.parse(ht.getString(i)), htemp.optDouble(i, 0.0), hpop.optInt(i, 0), hcode.optInt(i, 0))
        }
        val d = o.getJSONObject("daily")
        val dt = d.getJSONArray("time")
        val days = (0 until dt.length()).map { i ->
            Day(
                LocalDate.parse(dt.getString(i)),
                d.getJSONArray("weather_code").optInt(i, 0),
                d.getJSONArray("temperature_2m_max").optDouble(i, 0.0),
                d.getJSONArray("temperature_2m_min").optDouble(i, 0.0),
                runCatching { LocalDateTime.parse(d.getJSONArray("sunrise").getString(i)).toLocalTime() }.getOrNull(),
                runCatching { LocalDateTime.parse(d.getJSONArray("sunset").getString(i)).toLocalTime() }.getOrNull(),
                d.getJSONArray("uv_index_max").optDouble(i, 0.0),
            )
        }
        return Weather(
            temp = c.optDouble("temperature_2m"), feels = c.optDouble("apparent_temperature"),
            humidity = c.optInt("relative_humidity_2m"), wind = c.optDouble("wind_speed_10m"),
            windDir = c.optInt("wind_direction_10m"), pressure = c.optDouble("surface_pressure"),
            code = c.optInt("weather_code"), isDay = c.optInt("is_day", 1) == 1,
            hours = hours, days = days, fetched = fetched,
        )
    }

    // ---------- HTTP ----------
    private var factory: SSLSocketFactory? = null

    private fun get(ctx: Context, url: String): String {
        val conn = URL(url).openConnection() as HttpsURLConnection
        trustFactory(ctx)?.let { conn.sslSocketFactory = it }
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "AgendaMilo/0.1 (F-Droid)")
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    /**
     * Android < 7.1.1 no confía en ISRG Root X1 (Let's Encrypt). Si existe res/raw/isrgrootx1.pem
     * lo añadimos a los certificados del sistema solo para esta app.
     */
    private fun trustFactory(ctx: Context): SSLSocketFactory? {
        if (Build.VERSION.SDK_INT >= 25) return null
        factory?.let { return it }
        return runCatching {
            val id = ctx.resources.getIdentifier("isrgrootx1", "raw", ctx.packageName)
            if (id == 0) return null
            val ks = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            val sys = KeyStore.getInstance("AndroidCAStore").apply { load(null, null) }
            for (alias in sys.aliases()) ks.setCertificateEntry(alias, sys.getCertificate(alias))
            val cf = CertificateFactory.getInstance("X.509")
            ctx.resources.openRawResource(id).use { ks.setCertificateEntry("isrg_root_x1", cf.generateCertificate(it)) }
            val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(ks) }
            SSLContext.getInstance("TLS").apply { init(null, tmf.trustManagers, null) }.socketFactory
        }.getOrNull().also { factory = it }
    }
}
