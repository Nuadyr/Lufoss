package io.github.ardalorn.lufoss.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.ardalorn.lufoss.Cfg
import io.github.ardalorn.lufoss.data.Geocoder
import io.github.ardalorn.lufoss.data.Place
import io.github.ardalorn.lufoss.data.wantsNumber
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the list under a search box shows. */
sealed interface Suggest {
    data object Hidden : Suggest

    data object Searching : Suggest

    data class Hint(val text: String) : Suggest

    data class Found(val places: List<Place>, val note: String?) : Suggest

    data class Empty(val text: String) : Suggest
}

/** The country that suggestions are limited to. */
class CountryFilter(val code: String, val name: String)

/**
 * A search box: suggestions appear as you type (after a short pause), a submitted search asks the exact address
 * service, and clearing the box clears the suggestions.
 */
class SearchModel(
    private val scope: CoroutineScope,
    private val geocoder: Geocoder,
    private val filter: () -> CountryFilter?,
    private val say: (String) -> Unit,
    private val onError: (String) -> Unit
) {
    var query by mutableStateOf("")
    var state by mutableStateOf<Suggest>(Suggest.Hidden)
        private set

    /** Bumps whenever a list of places appears, so the screen can tell the person. */
    var shown by mutableIntStateOf(0)
        private set

    private var seq = 0
    private var debounce: Job? = null
    private var job: Job? = null

    fun onQueryChange(text: String) {
        query = text
        debounce?.cancel()
        val t = text.trim()
        if (t.length < Cfg.SUGGEST_MIN) {
            end()
            return
        }
        if (wantsNumber(t)) {
            job?.cancel()
            seq++
            state = Suggest.Hint("Press Search to find this exact address.")
            return
        }
        debounce = scope.launch {
            delay(Cfg.SUGGEST_DEBOUNCE_MS)
            run(t, exact = false)
        }
    }

    fun submit() {
        debounce?.cancel()
        val t = query.trim()
        if (t.length < Cfg.MIN_SUBMIT_CHARS) {
            end()
            return
        }
        run(t, exact = true)
    }

    /** Searches again with the current text, for when the country filter changes. */
    fun rerun() {
        val t = query.trim()
        if (t.length >= Cfg.SUGGEST_MIN) run(t, exact = false)
    }

    fun end() {
        debounce?.cancel()
        job?.cancel()
        seq++
        state = Suggest.Hidden
        say("")
    }

    private fun run(text: String, exact: Boolean) {
        val mine = ++seq
        job?.cancel()
        if (exact) say("Searching…")
        if (state !is Suggest.Found || exact) state = Suggest.Searching
        job = scope.launch {
            try {
                val country = filter()
                val result = geocoder.search(text, exact, country?.code)
                if (mine != seq) return@launch
                if (result.places.isEmpty()) {
                    val advice = if (country != null) ", or turn off “Only in ${country.name}”" else ""
                    state = Suggest.Empty("No places found for “$text”. Try another spelling$advice.")
                    say("No places found for “$text”.")
                } else {
                    state = Suggest.Found(result.places, result.note)
                    shown++
                    say("${result.places.size} places found.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (mine == seq) {
                    state = Suggest.Hidden
                    say("")
                    onError("Search failed. Check your connection and try again.")
                }
            }
        }
    }
}
