package io.github.nuadyr.lufoss.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.nuadyr.lufoss.Cfg
import io.github.nuadyr.lufoss.map.LocateMode
import io.github.nuadyr.lufoss.map.MapLayout

private const val TILE_ERROR_TEXT = "Map pictures could not load. Check your connection."
private const val COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"
private const val SHEET_MAX_SHARE = 0.6f
private const val LOCATE_NORTH_LABEL = "Following you, north at the top. Press to turn with you."
private const val LOCATE_HEADING_LABEL = "Following you, map turns with you. Press for north up."
private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)
private val sideMargin = 12.dp
private val panelWidth = 416.dp
private val attributionHeight = Cfg.ATTRIB_HEIGHT.dp

/** The whole screen: the map, the search bar, the buttons, the sheet and the dialogs. */
@Composable
fun LufossScreen(vm: LufossViewModel) {
    PermissionLauncher(vm)
    KeepScreenOn(vm.navActive)
    FitEffect(vm)
    BackControl(vm)

    val density = LocalDensity.current
    val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout)
    var bannerBottom by remember { mutableDoubleStateOf(0.0) }
    var sheetWidth by remember { mutableDoubleStateOf(0.0) }
    var sheetHeight by remember { mutableDoubleStateOf(0.0) }

    Box(Modifier.fillMaxSize()) {
        MapView(vm, Modifier.fillMaxSize())
        BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(bars)) {
            val wide = maxWidth >= Cfg.WIDE_LAYOUT_DP.dp
            val sheetVisible = vm.sheet != Sheet.None && vm.search.state is Suggest.Hidden && !vm.picking
            val topInset = with(density) { bars.getTop(density).toDp().value.toDouble() }
            val bottomInset = with(density) { bars.getBottom(density).toDp().value.toDouble() }
            // Read here, in composition, so that a change in any of them runs the side effect again.
            val banner = if (vm.navActive) bannerBottom else 0.0
            val sheetW = sheetWidth
            val sheetH = sheetHeight
            SideEffect {
                vm.map.setLayout(
                    MapLayout(
                        wide = wide,
                        topInset = topInset,
                        bottomInset = bottomInset,
                        bannerBottom = banner,
                        sheetWidth = sheetW,
                        sheetHeight = sheetH,
                        sheetVisible = sheetVisible
                    )
                )
            }
            Controls(vm)
            if (vm.navActive) {
                NavBanner(
                    ui = vm.navUi,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(sideMargin)
                        .widthIn(max = panelWidth)
                        .fillMaxWidth()
                        .onGloballyPositioned { bannerBottom = it.boundsInRoot().bottom / density.density.toDouble() }
                )
            } else {
                SearchColumn(vm)
            }
            BottomPanel(
                vm = vm,
                wide = wide,
                sheetVisible = sheetVisible,
                onSheetSize = { w, h ->
                    sheetWidth = w
                    sheetHeight = h
                }
            )
            Attribution()
        }
        StatusLine(vm.status)
    }
    StartDialog(vm)
    AboutDialog(vm)
}

// ---- Effects ----

@Composable
private fun PermissionLauncher(vm: LufossViewModel) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.onPermissionResult(result.values.any { it })
    }
    LaunchedEffect(vm.permRequest) {
        if (vm.permRequest > 0) {
            launcher.launch(LOCATION_PERMISSIONS)
        }
    }
}

/** Keeps the screen on while a walk is in progress. */
@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/** Fits a new route into the free part of the map, once the sheet has been laid out. */
@Composable
private fun FitEffect(vm: LufossViewModel) {
    LaunchedEffect(vm.fitRequest) {
        if (vm.fitRequest > 0) {
            withFrameNanos { }
            withFrameNanos { }
            vm.performFit()
        }
    }
}

/** Back closes things in order. While walking it does nothing, so a stray swipe cannot end the walk. */
@Composable
private fun BackControl(vm: LufossViewModel) {
    val active = vm.picking || vm.navActive || vm.sheet != Sheet.None || vm.search.state !is Suggest.Hidden
    BackHandler(enabled = active) {
        when {
            vm.picking -> vm.endPick(true)
            vm.search.state !is Suggest.Hidden -> vm.search.end()
            vm.navActive -> Unit
            else -> vm.closeSheet()
        }
    }
}

// ---- Pieces ----

/** Text for screen readers only. It announces progress, results and turns. */
@Composable
private fun StatusLine(text: String) {
    if (text.isEmpty()) return
    Text(
        text = text,
        color = Color.Transparent,
        fontSize = 1.sp,
        modifier = Modifier
            .size(1.dp)
            .semantics { liveRegion = LiveRegionMode.Polite }
    )
}

@Composable
private fun BoxWithConstraintsScope.Controls(vm: LufossViewModel) {
    val map = vm.map
    Column(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = sideMargin, top = if (vm.navActive) 120.dp else 80.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompassFab(vm)
        if (vm.navActive) {
            when (map.locateMode) {
                LocateMode.FREE -> MapFab(LufossIcons.locateFree, "Follow me", vm::onLocate)
                LocateMode.NORTH -> MapFab(LufossIcons.locateNorth, LOCATE_NORTH_LABEL, vm::onLocate)
                LocateMode.HEADING -> MapFab(LufossIcons.locateHeading, LOCATE_HEADING_LABEL, vm::onLocate)
            }
        } else {
            MapFab(LufossIcons.flag, "Set start point", { vm.openStart() })
            MapFab(LufossIcons.loop, "Plan a round-trip walk", vm::showLoopPicker)
        }
        MapFab(LufossIcons.plus, "Zoom in", { map.zoomCentre(Cfg.ZOOM_STEP) })
        MapFab(LufossIcons.minus, "Zoom out", { map.zoomCentre(-Cfg.ZOOM_STEP) })
        MapFab(LufossIcons.info, "About Lufoss", { vm.aboutOpen = true })
    }
}

@Composable
private fun BoxWithConstraintsScope.SearchColumn(vm: LufossViewModel) {
    // Read here: inside the Column lambda the compiler hides this scope's maxHeight.
    val listMax = maxHeight * SEARCH_LIST_SHARE
    Column(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(sideMargin)
            .widthIn(max = panelWidth)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = vm.search.query,
            onValueChange = vm.search::onQueryChange,
            placeholder = { Text("Search for a place or address") },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.search.submit() }),
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (vm.search.query.isNotEmpty()) {
                        IconButton(onClick = { vm.search.onQueryChange("") }) {
                            Icon(LufossIcons.clear, contentDescription = "Clear search")
                        }
                    }
                    IconButton(onClick = vm.search::submit) {
                        Icon(LufossIcons.search, contentDescription = "Search")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        if (vm.home.isNotEmpty()) {
            ToggleChip("Only in ${vm.homeName}", vm.localOnly, vm::toggleLocalOnly)
        }
        SuggestionList(vm.search.state, vm::pickResult, maxHeight = listMax)
    }
}

private const val SEARCH_LIST_SHARE = 0.45f

@Composable
private fun BoxWithConstraintsScope.BottomPanel(
    vm: LufossViewModel,
    wide: Boolean,
    sheetVisible: Boolean,
    onSheetSize: (Double, Double) -> Unit
) {
    val density = LocalDensity.current.density
    // Read here: inside the Column lambda the compiler hides this scope's maxHeight.
    val sheetMax = maxHeight * SHEET_MAX_SHARE
    val base = if (wide) {
        Modifier.align(Alignment.BottomStart).width(panelWidth + sideMargin)
    } else {
        Modifier.align(Alignment.BottomCenter).fillMaxWidth()
    }
    Column(
        modifier = base.padding(bottom = attributionHeight),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val message = vm.error.ifEmpty { if (vm.tileError) TILE_ERROR_TEXT else "" }
        if (message.isNotEmpty()) {
            Surface(
                modifier = Modifier.padding(horizontal = sideMargin).fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                ErrorText(message, Modifier.padding(14.dp))
            }
        }
        vm.perm?.let { card -> PermissionCard(vm, card, Modifier.padding(horizontal = sideMargin)) }
        if (vm.picking) PickCard(vm, Modifier.padding(horizontal = sideMargin))
        if (sheetVisible) {
            SheetSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = sheetMax)
                    .onSizeChanged { onSheetSize(it.width / density.toDouble(), it.height / density.toDouble()) },
                wide = wide
            ) {
                SheetContent(vm)
            }
        }
    }
}

@Composable
private fun SheetContent(vm: LufossViewModel) {
    val sheet = vm.sheet
    val dest = vm.dest
    val route = vm.route
    when {
        sheet is Sheet.PlaceCard && dest != null -> PlaceSheet(vm, dest)
        sheet is Sheet.LoopPicker -> LoopSheet(vm)
        sheet is Sheet.Plan && route != null && dest != null -> PlanSheet(vm, route, dest)
        sheet is Sheet.Walking -> WalkingSheet(vm)
        sheet is Sheet.Arrived -> ArrivedSheet(vm, sheet.name)
    }
}

@Composable
private fun PermissionCard(vm: LufossViewModel, card: PermCard, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    InfoCard(modifier) {
        if (card == PermCard.ASK) {
            Text(
                text = "Allow location?",
                style = MaterialTheme.typography.titleMedium,
                color = LocalLufossColors.current.heading
            )
            Text(
                text = "Lufoss uses your position only while you walk, to follow your progress along the route, " +
                    "or when you choose GPS as your start point. It stays on this device and is never saved. " +
                    "Android will ask you to confirm.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::onPermAllow, modifier = Modifier.weight(1f)) { Text("Allow location") }
                OutlinedButton(onClick = vm::onPermSkip, modifier = Modifier.weight(1f)) { Text("Not now") }
            }
        } else {
            Text(
                text = "Location is blocked",
                style = MaterialTheme.typography.titleMedium,
                color = LocalLufossColors.current.heading
            )
            Text(
                text = "Android is blocking location for Lufoss. Open the app's settings, set Location to Allow, " +
                    "then try again.",
                style = MaterialTheme.typography.bodyMedium
            )
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                Button(onClick = vm::onPermAllow, modifier = Modifier.weight(1f)) { Text("Try again") }
                OutlinedButton(
                    onClick = { context.startActivity(appSettingsIntent(context.packageName)) },
                    modifier = Modifier.weight(1f)
                ) { Text("Settings") }
            }
            OutlinedButton(onClick = vm::onPermSkip, modifier = Modifier.fillMaxWidth()) { Text("Close") }
        }
    }
}

private fun appSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))

@Composable
private fun PickCard(vm: LufossViewModel, modifier: Modifier = Modifier) {
    InfoCard(modifier) {
        Text(
            text = "Tap the map to set your start",
            style = MaterialTheme.typography.titleMedium,
            color = LocalLufossColors.current.heading
        )
        Text(
            text = "Move and zoom the map, then tap where your walks should begin.",
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedButton(onClick = { vm.endPick(true) }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

/** The map credit the OpenStreetMap licence asks for, along the bottom edge. */
@Composable
private fun BoxWithConstraintsScope.Attribution() {
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(attributionHeight)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = ATTRIBUTION_ALPHA))
            .clickable(onClickLabel = "Open the OpenStreetMap copyright page") { uriHandler.openUri(COPYRIGHT_URL) },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Map pictures © OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val ATTRIBUTION_ALPHA = 0.85f
