package com.sakina.app.ui.location

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sakina.app.core.location.City
import com.sakina.app.core.location.CitySearch
import com.sakina.app.data.AppClock
import com.sakina.app.data.AppSettings
import com.sakina.app.data.LocationSource
import com.sakina.app.data.SavedLocation
import com.sakina.app.data.SettingsOps
import com.sakina.app.ui.GpsUi
import com.sakina.app.ui.LocalFmt
import com.sakina.app.ui.LocalStrings
import com.sakina.app.ui.MainViewModel
import com.sakina.app.ui.PlacePurpose
import com.sakina.app.ui.Route
import com.sakina.app.ui.components.Banner
import com.sakina.app.ui.components.Divider
import com.sakina.app.ui.components.NoteText
import com.sakina.app.ui.components.SakinaIcons
import com.sakina.app.ui.components.ScreenBar
import com.sakina.app.ui.components.SectionCard
import java.time.ZoneId
import java.util.Locale

/** Applies a confirmed place: either adds it to saved places, or uses it as the travel destination. */
fun applyPlace(vm: MainViewModel, purpose: PlacePurpose, place: SavedLocation) {
    when (purpose) {
        PlacePurpose.ADD -> vm.update { SettingsOps.addAndActivate(it, place) }
        PlacePurpose.TRAVEL -> vm.update { SettingsOps.startTravel(it, place, AppClock.now().toEpochMilli()) }
    }
    vm.popTo { it is Route.Locations }
}

@Composable
fun FullScreen(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()) { content() }
}

@Composable
fun CityPickerScreen(vm: MainViewModel, purpose: PlacePurpose) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val cities by vm.cities.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var pending by remember { mutableStateOf<City?>(null) }
    val results = remember(cities, query) { CitySearch.search(cities, query, limit = 80) }
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(if (purpose == PlacePurpose.TRAVEL) str.travelDestination else str.pickCity, onBack = { vm.pop() })
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(str.searchCities) },
                leadingIcon = { Icon(SakinaIcons.Search, null) },
                trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(SakinaIcons.Close, str.close) } }) else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("city_search"),
            )
            Spacer(Modifier.height(8.dp))
            if (cities.isNotEmpty() && results.isEmpty()) {
                Banner(str.noResults, modifier = Modifier.padding(16.dp))
                TextButton(onClick = { vm.push(Route.Manual(purpose)) }, modifier = Modifier.padding(horizontal = 16.dp)) { Text(str.manualEntry) }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { if (purpose == PlacePurpose.TRAVEL) pending = c else applyPlace(vm, purpose, SettingsOps.cityLocation(c)) }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(SakinaIcons.Pin, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (str.isAr) c.nameAr else c.nameEn, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                (if (str.isAr) c.countryAr else c.countryEn) + " · " + (if (str.isAr) c.nameEn else c.nameAr),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(fmt.utcOffset(ZoneId.of(c.zone), AppClock.now()), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    pending?.let { c ->
        ConfirmPlaceDialog(SettingsOps.cityLocation(c).copy(id = SettingsOps.newId("trip")), purpose, onDismiss = { pending = null }) {
            pending = null
            applyPlace(vm, purpose, it)
        }
    }
}

@Composable
fun ConfirmPlaceDialog(place: SavedLocation, purpose: PlacePurpose, onDismiss: () -> Unit, onConfirm: (SavedLocation) -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str.confirmPlace(place.name(str.lang))) },
        text = {
            Column {
                Text(str.coordinates + ": " + fmt.coordinate(place.latitude, true) + "، " + fmt.coordinate(place.longitude, false), style = MaterialTheme.typography.bodyMedium)
                Text(str.timeZone + ": " + place.zone + " (" + (place.zoneId?.let { fmt.utcOffset(it, AppClock.now()) } ?: "?") + ")", style = MaterialTheme.typography.bodyMedium)
                if (purpose == PlacePurpose.TRAVEL) {
                    Spacer(Modifier.height(8.dp))
                    NoteText(str.travelExplain)
                }
            }
        },
        confirmButton = { Button(onClick = { onConfirm(place) }) { Text(if (purpose == PlacePurpose.TRAVEL) str.useAsDestination else str.usePlace) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(str.cancel) } },
    )
}

@Composable
fun ZonePickerDialog(current: String?, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var q by remember { mutableStateOf("") }
    val zones = remember { ZoneId.getAvailableZoneIds().filter { it.contains('/') && !it.startsWith("Etc") && !it.startsWith("SystemV") }.sorted() }
    val filtered = remember(q) {
        val needle = q.trim().lowercase(Locale.ROOT).replace(' ', '_')
        if (needle.isEmpty()) zones else zones.filter { it.lowercase(Locale.ROOT).contains(needle) }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(str.chooseZone) },
        text = {
            Column {
                OutlinedTextField(value = q, onValueChange = { q = it }, singleLine = true, placeholder = { Text(str.searchZones) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(filtered, key = { it }) { z ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { onPick(z) }.padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(z, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), color = if (z == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            Text(fmt.utcOffset(ZoneId.of(z), AppClock.now()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(str.cancel) } },
    )
}

/** Shared form for manual entry and for confirming a GPS fix. */
@Composable
private fun PlaceForm(
    purpose: PlacePurpose,
    initialNameAr: String,
    initialNameEn: String,
    initialLat: String,
    initialLon: String,
    initialZone: String?,
    source: LocationSource,
    note: String?,
    vm: MainViewModel,
    suggestedZone: Pair<String, String>? = null,
) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var nameAr by rememberSaveable { mutableStateOf(initialNameAr) }
    var nameEn by rememberSaveable { mutableStateOf(initialNameEn) }
    var lat by rememberSaveable { mutableStateOf(initialLat) }
    var lon by rememberSaveable { mutableStateOf(initialLon) }
    var zone by rememberSaveable { mutableStateOf(initialZone) }
    var showZones by remember { mutableStateOf(false) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val latV = lat.trim().replace('٫', '.').replace(',', '.').toDoubleOrNull()?.takeIf { it in -90.0..90.0 }
    val lonV = lon.trim().replace('٫', '.').replace(',', '.').toDoubleOrNull()?.takeIf { it in -180.0..180.0 }
    val nameOk = nameAr.isNotBlank() || nameEn.isNotBlank()
    val valid = latV != null && lonV != null && zone != null && nameOk

    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (note != null) Banner(note)
        OutlinedTextField(nameAr, { nameAr = it }, label = { Text(str.placeNameAr) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("name_ar"),
            isError = tried && !nameOk, supportingText = if (tried && !nameOk) ({ Text(str.nameRequired) }) else null)
        OutlinedTextField(nameEn, { nameEn = it }, label = { Text(str.placeNameEn) }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("name_en"))
        OutlinedTextField(lat, { lat = it }, label = { Text(str.latitude) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("lat"),
            readOnly = source == LocationSource.GPS,
            isError = tried && latV == null, supportingText = if (tried && latV == null) ({ Text(str.invalidLat) }) else null)
        OutlinedTextField(lon, { lon = it }, label = { Text(str.longitude) }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth().testTag("lon"),
            readOnly = source == LocationSource.GPS,
            isError = tried && lonV == null, supportingText = if (tried && lonV == null) ({ Text(str.invalidLon) }) else null)
        OutlinedButton(onClick = { showZones = true }, modifier = Modifier.fillMaxWidth().testTag("zone")) {
            Icon(SakinaIcons.Globe, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(zone?.let { z -> z + " (" + fmt.utcOffset(ZoneId.of(z), AppClock.now()) + ")" } ?: str.chooseZone)
        }
        if (suggestedZone != null && suggestedZone.second != zone) {
            TextButton(onClick = { zone = suggestedZone.second }) { Text(str.useCityZone(suggestedZone.first, fmt.ltr(suggestedZone.second))) }
        }
        if (tried && zone == null) Text(str.zoneRequired, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        if (source == LocationSource.GPS) NoteText(str.gpsZoneNote)
        Button(
            onClick = {
                tried = true
                if (valid) {
                    val place = SavedLocation(
                        id = SettingsOps.newId(if (purpose == PlacePurpose.TRAVEL) "trip" else if (source == LocationSource.GPS) "gps" else "manual"),
                        nameAr = nameAr.trim().ifBlank { nameEn.trim() },
                        nameEn = nameEn.trim().ifBlank { nameAr.trim() },
                        latitude = latV!!,
                        longitude = lonV!!,
                        zone = zone!!,
                        source = source,
                    )
                    applyPlace(vm, purpose, place)
                }
            },
            modifier = Modifier.fillMaxWidth().testTag("save_place"),
        ) { Text(if (purpose == PlacePurpose.TRAVEL) str.useAsDestination else str.usePlace) }
        if (purpose == PlacePurpose.TRAVEL) NoteText(str.travelExplain)
    }
    if (showZones) ZonePickerDialog(zone, onDismiss = { showZones = false }) { zone = it; showZones = false }
}

@Composable
fun ManualPlaceScreen(vm: MainViewModel, purpose: PlacePurpose) {
    val str = LocalStrings.current
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.manualTitle, onBack = { vm.pop() })
            PlaceForm(purpose, "", "", "", "", null, LocationSource.MANUAL, null, vm)
        }
    }
}

@Composable
fun GpsPlaceScreen(vm: MainViewModel, purpose: PlacePurpose) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    val gps by vm.gps.collectAsStateWithLifecycle()
    val cities by vm.cities.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) vm.locate() else vm.gpsDenied()
    }
    LaunchedEffect(Unit) {
        vm.resetGps()
        if (vm.hasLocationPermission()) vm.locate()
        else launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
    }
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.gpsTitle, onBack = { vm.resetGps(); vm.pop() })
            when (val g = gps) {
                GpsUi.Idle, GpsUi.Locating -> Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(16.dp))
                    Text(str.gpsLocating, style = MaterialTheme.typography.bodyLarge)
                }
                is GpsUi.Found -> {
                    val near = remember(g, cities) { CitySearch.nearest(cities, g.latitude, g.longitude) }
                    val nameAr = near?.let { "قرب " + it.nameAr } ?: "موقعي"
                    val nameEn = near?.let { "Near " + it.nameEn } ?: "My location"
                    val note = g.accuracy?.let { str.gpsAccuracy(fmt.num(it.toInt())) }
                    PlaceForm(
                        purpose, nameAr, nameEn,
                        String.format(Locale.US, "%.5f", g.latitude), String.format(Locale.US, "%.5f", g.longitude),
                        ZoneId.systemDefault().id, LocationSource.GPS, note, vm,
                        suggestedZone = near?.let { (if (str.isAr) it.nameAr else it.nameEn) to it.zone },
                    )
                }
                else -> Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Banner(
                        when (g) {
                            GpsUi.Disabled -> str.gpsDisabled
                            GpsUi.TimedOut -> str.gpsTimeout
                            else -> str.gpsDenied
                        },
                        SakinaIcons.Warning,
                        warning = true,
                    )
                    Button(onClick = {
                        if (vm.hasLocationPermission()) vm.locate()
                        else launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    }, modifier = Modifier.fillMaxWidth()) { Text(str.retry) }
                    OutlinedButton(onClick = { vm.pop(); vm.push(Route.CityPicker(purpose)) }, modifier = Modifier.fillMaxWidth()) { Text(str.pickCity) }
                    OutlinedButton(onClick = { vm.pop(); vm.push(Route.Manual(purpose)) }, modifier = Modifier.fillMaxWidth()) { Text(str.manualEntry) }
                }
            }
        }
    }
}

@Composable
fun LocationsScreen(vm: MainViewModel, s: AppSettings) {
    val str = LocalStrings.current
    val fmt = LocalFmt.current
    var confirmDelete by remember { mutableStateOf<SavedLocation?>(null) }
    FullScreen {
        Column(Modifier.fillMaxSize()) {
            ScreenBar(str.locations, onBack = { vm.pop() })
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SectionCard(title = str.savedPlaces, icon = SakinaIcons.Pin) {
                    if (s.locations.isEmpty()) NoteText(str.chooseLocationBody)
                    s.locations.forEachIndexed { i, loc ->
                        if (i > 0) Divider()
                        val active = s.travel == null && s.activeLocation?.id == loc.id
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.update { SettingsOps.activate(SettingsOps.returnHome(it), loc.id) } }.padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(selected = active, onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(loc.name(str.lang), style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    listOfNotNull(loc.country(str.lang), loc.zone).joinToString(" · ") + " · " + fmt.coordinate(loc.latitude, true) + " " + fmt.coordinate(loc.longitude, false),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                                )
                            }
                            IconButton(onClick = { confirmDelete = loc }) { Icon(SakinaIcons.Trash, str.delete, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                SectionCard(title = str.addPlace, icon = SakinaIcons.Plus) {
                    Button(onClick = { vm.push(Route.CityPicker(PlacePurpose.ADD)) }, modifier = Modifier.fillMaxWidth()) { Text(str.pickCity) }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.push(Route.Gps(PlacePurpose.ADD)) }, modifier = Modifier.weight(1f)) { Text(str.useGps, maxLines = 1) }
                        OutlinedButton(onClick = { vm.push(Route.Manual(PlacePurpose.ADD)) }, modifier = Modifier.weight(1f)) { Text(str.manualEntry, maxLines = 1) }
                    }
                }
                SectionCard(title = str.travelling, icon = SakinaIcons.Suitcase) {
                    val travel = s.travel
                    if (travel == null) {
                        NoteText(str.travelExplain)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.push(Route.CityPicker(PlacePurpose.TRAVEL)) }, enabled = s.locations.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(str.startTravel, maxLines = 1) }
                            OutlinedButton(onClick = { vm.push(Route.Gps(PlacePurpose.TRAVEL)) }, enabled = s.locations.isNotEmpty(), modifier = Modifier.weight(1f)) { Text(str.useGps, maxLines = 1) }
                        }
                        TextButton(onClick = { vm.push(Route.Manual(PlacePurpose.TRAVEL)) }, enabled = s.locations.isNotEmpty()) { Text(str.manualEntry) }
                    } else {
                        Text(str.travelDestination + ": " + travel.destination.name(str.lang), style = MaterialTheme.typography.bodyLarge)
                        s.homeLocation?.let { Text(str.homeIs(it.name(str.lang)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { vm.update { SettingsOps.returnHome(it) } }, modifier = Modifier.fillMaxWidth()) { Text(str.returnHome) }
                        if (s.locations.none { it.id == travel.destination.id }) {
                            TextButton(onClick = { vm.update { SettingsOps.keepDestination(it) } }) { Text(str.keepDestination) }
                        }
                    }
                }
            }
        }
    }
    confirmDelete?.let { loc ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(str.deletePlaceQ) },
            text = { Text(loc.name(str.lang)) },
            confirmButton = { TextButton(onClick = { vm.update { SettingsOps.remove(it, loc.id) }; confirmDelete = null }) { Text(str.delete) } },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(str.cancel) } },
        )
    }
}
