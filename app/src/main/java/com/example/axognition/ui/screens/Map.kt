package com.example.axognition.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DefaultMapLocation = LatLng(-22.5609, 17.0658)
private const val SAVED_AREAS_KEY = "saved_map_areas"

private data class SavedMapArea(val name: String, val latitude: Double, val longitude: Double, val zoom: Float)

/** Google Maps with roads, business/place labels, location, search and local saved-area shortcuts. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(DefaultMapLocation, 12f)
    }
    val savedAreas = remember { mutableStateListOf<SavedMapArea>().also { it += loadSavedAreas(context) } }
    var query by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var areaName by remember { mutableStateOf("") }
    var locationPermissionGranted by remember { mutableStateOf(context.hasLocationPermission()) }
    val hasMapsApiKey = remember(context) { context.hasMapsApiKey() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (locationPermissionGranted) moveToCurrentLocation(context, cameraPositionState)
        else Toast.makeText(context, "Location permission was not granted", Toast.LENGTH_SHORT).show()
    }

    fun search() {
        val searchText = query.trim()
        if (searchText.isEmpty()) return
        scope.launch {
            isSearching = true
            val result = findPlace(context, searchText)
            isSearching = false
            if (result == null) Toast.makeText(context, "No place found for \"$searchText\"", Toast.LENGTH_SHORT).show()
            else cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(result, 16f))
        }
    }

    LaunchedEffect(hasMapsApiKey) {
        if (hasMapsApiKey && locationPermissionGranted) moveToCurrentLocation(context, cameraPositionState)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Explore map", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Go back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
            )
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (hasMapsApiKey) {
                GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                properties = MapProperties(isMyLocationEnabled = locationPermissionGranted),
                uiSettings = MapUiSettings(myLocationButtonEnabled = false, zoomControlsEnabled = false, compassEnabled = true)
                ) {
                    savedAreas.forEach { area ->
                        Marker(MarkerState(LatLng(area.latitude, area.longitude)), title = area.name, snippet = "Saved map area")
                    }
                }

                Column(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)),
                        placeholder = { Text("Search places, shops or addresses") },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search") } },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { search() })
                    )
                    if (isSearching) Text("Finding places…", Modifier.padding(start = 16.dp, top = 6.dp), style = MaterialTheme.typography.labelMedium)
                }

                Column(
                    Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FloatingActionButton(
                        onClick = {
                            if (locationPermissionGranted) moveToCurrentLocation(context, cameraPositionState)
                            else permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) { Icon(Icons.Default.MyLocation, "Show my location") }
                    FloatingActionButton(
                        onClick = { areaName = ""; showSaveDialog = true },
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) { Icon(Icons.Default.BookmarkAdd, "Save this map area") }
                }

                if (savedAreas.isNotEmpty()) Card(
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f))
                ) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Saved areas", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(8.dp))
                        savedAreas.take(2).forEach { area ->
                            AssistChip(
                                onClick = { scope.launch { cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(LatLng(area.latitude, area.longitude), area.zoom)) } },
                                label = { Text(area.name, maxLines = 1) },
                                modifier = Modifier.padding(end = 4.dp)
                            )
                        }
                    }
                }
            } else {
                MapApiKeyRequired(Modifier.fillMaxSize().padding(24.dp))
            }
        }
    }

    if (showSaveDialog) AlertDialog(
        onDismissRequest = { showSaveDialog = false },
        title = { Text("Save map area") },
        text = {
            Column {
                Text("Save the current map position on this device for quick access later.")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(areaName, { areaName = it }, label = { Text("Area name") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val position = cameraPositionState.position
                savedAreas += SavedMapArea(areaName.trim().ifBlank { "Saved area" }, position.target.latitude, position.target.longitude, position.zoom)
                persistSavedAreas(context, savedAreas)
                showSaveDialog = false
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } }
    )
}

private fun Context.hasLocationPermission() =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun Context.hasMapsApiKey(): Boolean {
    val apiKey = runCatching {
        packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA)
            .metaData?.getString("com.google.android.geo.API_KEY")
            ?.trim()
    }.getOrNull()
    return apiKey?.let { it.isNotBlank() && !it.contains("MAPS_API_KEY") } == true
}

@Composable
private fun MapApiKeyRequired(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Map setup needed", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Add a Google Maps Android API key to secrets.properties, then rebuild the app.")
                Text("MAPS_API_KEY=your_google_maps_android_key", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun moveToCurrentLocation(context: Context, cameraPositionState: CameraPositionState) {
    LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location ->
        location?.let { cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 16f)) }
    }
}

@Suppress("DEPRECATION")
private suspend fun findPlace(context: Context, query: String): LatLng? = withContext(Dispatchers.IO) {
    runCatching {
        Geocoder(context).getFromLocationName(query, 1)?.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
    }.getOrNull()
}

private fun loadSavedAreas(context: Context): List<SavedMapArea> =
    context.getSharedPreferences("map_preferences", Context.MODE_PRIVATE).getStringSet(SAVED_AREAS_KEY, emptySet()).orEmpty().mapNotNull { item ->
        item.split("|", limit = 4).takeIf { it.size == 4 }?.let { parts ->
            runCatching { SavedMapArea(parts[0], parts[1].toDouble(), parts[2].toDouble(), parts[3].toFloat()) }.getOrNull()
        }
    }

private fun persistSavedAreas(context: Context, areas: List<SavedMapArea>) {
    context.getSharedPreferences("map_preferences", Context.MODE_PRIVATE).edit()
        .putStringSet(SAVED_AREAS_KEY, areas.map { "${it.name}|${it.latitude}|${it.longitude}|${it.zoom}" }.toSet()).apply()
}
