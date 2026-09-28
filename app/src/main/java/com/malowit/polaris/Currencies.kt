package com.malowit.polaris
import androidx.core.content.edit
data class Currency(val code: String, val symbol: String)

val SUPPORTED_CURRENCIES = listOf(
    Currency("KZT", "₸"),
    Currency("USD", "$"),
    Currency("EUR", "€"),
    Currency("CNY", "¥"),
    Currency("VND", "₫"),
    Currency("THB", "฿"),
    Currency("IDR", "Rp"),
    Currency("TRY", "₺"),
    Currency("JPY", "¥"),
    Currency("KRW", "₩"),
    Currency("AED", "AED"),
    Currency("GBP", "£"),
    Currency("RUB", "₽"),
    Currency("INR", "₹"),
    Currency("AUD", "A$"),
    Currency("CAD", "C$"),
    Currency("CHF", "CHF"),
    Currency("SGD", "S$"),
    Currency("HKD", "HK$"),
    Currency("MYR", "RM"),
    Currency("PHP", "₱"),
    Currency("MXN", "MX$"),
    Currency("BRL", "R$"),
    Currency("ZAR", "R"),
    Currency("EGP", "E£"),
    Currency("SAR", "SAR"),
    Currency("QAR", "QAR"),
    Currency("ILS", "₪"),
    Currency("PLN", "zł"),
    Currency("CZK", "Kč"),
    Currency("HUF", "Ft"),
    Currency("SEK", "kr"),
    Currency("NOK", "kr"),
    Currency("DKK", "kr"),
    Currency("UAH", "₴"),
    Currency("GEL", "₾"),
    Currency("AMD", "֏"),
    Currency("AZN", "₼"),
    Currency("UZS", "so'm"),
    Currency("KGS", "с"),
    Currency("TJS", "ЅМ"),
    Currency("MNT", "₮"),
    Currency("LKR", "Rs"),
    Currency("NPR", "Rs"),
    Currency("PKR", "Rs"),
    Currency("BDT", "৳"),
    Currency("NZD", "NZ$"),
)

val POPULAR_LOCAL_CURRENCIES = listOf("USD", "EUR", "KZT", "CNY", "UAH")
val POPULAR_HOME_CURRENCIES = listOf("USD", "EUR", "KZT", "CNY", "UAH")

private const val SETTINGS_PREFS = "polaris_settings"

fun saveCurrencyPair(context: android.content.Context, local: String, home: String) {
    context.getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE).edit {
        putString("local_currency", local)
        putString("home_currency", home)
    }
}

fun getCurrencyPair(context: android.content.Context): Pair<String, String>? {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
    val local = prefs.getString("local_currency", null)
    val home = prefs.getString("home_currency", null)
    return if (local != null && home != null) Pair(local, home) else null
}

fun setBubbleEnabled(context: android.content.Context, enabled: Boolean) {
    context.getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE).edit {
        putBoolean("bubble_enabled", enabled)
    }
}

fun isBubbleEnabled(context: android.content.Context): Boolean {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
    return prefs.getBoolean("bubble_enabled", false)
}

fun getCurrencyNameResId(code: String): Int {
    val resName = "currency_name_${code.lowercase()}"
    return try {
        R.string::class.java.getField(resName).getInt(null)
    } catch (e: Exception) {
        0
    }
}

fun formatCurrencyLabel(context: android.content.Context, currency: Currency): String {
    val resId = getCurrencyNameResId(currency.code)
    val name = if (resId != 0) context.getString(resId) else currency.code
    return "${currency.symbol} ${currency.code} — $name"
}

fun formatCurrencyShort(currency: Currency): String = "${currency.symbol} ${currency.code}"

fun currencyFromLabel(label: String): Currency? {
    val code = label.split(" ").getOrNull(1)
    return SUPPORTED_CURRENCIES.find { it.code == code }
}

class CurrencyAutoCompleteAdapter(
    context: android.content.Context,
    private val popularCodes: List<String>
) : android.widget.ArrayAdapter<String>(context, R.layout.spinner_item_white, mutableListOf()) {

    private val appContext = context.applicationContext
    private var currentResults: List<String> = emptyList()

    override fun getCount(): Int = currentResults.size
    override fun getItem(position: Int): String = currentResults[position]

    override fun getFilter(): android.widget.Filter {
        return object : android.widget.Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val query = constraint?.toString()?.trim()?.lowercase() ?: ""
                val results = if (query.isEmpty()) {
                    popularCodes.mapNotNull { code -> SUPPORTED_CURRENCIES.find { it.code == code } }
                        .map { formatCurrencyLabel(appContext, it) }
                } else {
                    SUPPORTED_CURRENCIES.filter { currency ->
                        val resId = getCurrencyNameResId(currency.code)
                        val name = if (resId != 0) appContext.getString(resId) else ""
                        currency.code.lowercase().contains(query) || name.lowercase().contains(query)
                    }.map { formatCurrencyLabel(appContext, it) }
                }
                return FilterResults().apply {
                    values = results
                    count = results.size
                }
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                currentResults = results?.values as? List<String> ?: emptyList()
                notifyDataSetChanged()
            }
        }
    }
}