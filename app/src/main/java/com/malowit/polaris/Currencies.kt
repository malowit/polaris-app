package com.malowit.polaris

import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.edit

data class Currency(val code: String, val name: String, val symbol: String)

class CurrencySpinnerAdapter(context: android.content.Context, items: List<String>) :
    ArrayAdapter<String>(context, R.layout.spinner_item_white, items) {

    override fun isEnabled(position: Int): Boolean {
        return position != 0
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getDropDownView(position, convertView, parent) as TextView
        view.alpha = if (position == 0) 0.4f else 1f
        return view
    }
}
val SUPPORTED_CURRENCIES = listOf(
    Currency("KZT", "Тенге", "₸"),
    Currency("USD", "US Dollar", "$"),
    Currency("EUR", "Euro", "€"),
    Currency("CNY", "Chinese Yuan", "¥"),
    Currency("VND", "Đồng", "₫"),
    Currency("THB", "Baht", "฿"),
    Currency("IDR", "Rupiah", "Rp"),
    Currency("TRY", "Turkish Lira", "₺"),
    Currency("JPY", "Japanese Yen", "¥"),
    Currency("KRW", "South Korean Won", "₩"),
    Currency("AED", "UAE Dirham", "AED"),
)

private const val SETTINGS_PREFS = "polaris_settings"

fun saveCurrencyPair(context: android.content.Context, local: String, home: String) {
    context.getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
        .edit {
            putString("local_currency", local)
                .putString("home_currency", home)
        }
}

fun getCurrencyPair(context: android.content.Context): Pair<String, String>? {
    val prefs = context.getSharedPreferences(SETTINGS_PREFS, android.content.Context.MODE_PRIVATE)
    val local = prefs.getString("local_currency", null)
    val home = prefs.getString("home_currency", null)
    return if (local != null && home != null) Pair(local, home) else null
}