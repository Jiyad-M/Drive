package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.RoadObstacleEntity
import org.json.JSONArray
import org.json.JSONObject

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RealOsmMapView(
    latitude: Double,
    longitude: Double,
    heading: Float,
    speedKmH: Float,
    dynamicAlertDistance: Double,
    obstacles: List<RoadObstacleEntity>,
    onAddObstacleAtLocation: ((Double, Double, String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true

            addJavascriptInterface(object {
                @JavascriptInterface
                fun onMapClicked(lat: Double, lng: Double) {
                    onAddObstacleAtLocation?.invoke(lat, lng, "SPEED_BUMP")
                }
            }, "AndroidBridge")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    updateMapData(this@apply, latitude, longitude, heading, dynamicAlertDistance, obstacles)
                }
            }

            val html = generateLeafletHtml(latitude, longitude)
            loadDataWithBaseURL("https://osm.org", html, "text/html", "UTF-8", null)
        }
    }

    LaunchedEffect(latitude, longitude, heading, dynamicAlertDistance, obstacles) {
        updateMapData(webView, latitude, longitude, heading, dynamicAlertDistance, obstacles)
    }

    AndroidView(
        factory = { webView },
        modifier = modifier.fillMaxSize()
    )
}

private fun updateMapData(
    webView: WebView,
    lat: Double,
    lng: Double,
    heading: Float,
    alertDistance: Double,
    obstacles: List<RoadObstacleEntity>
) {
    if (lat == 0.0 && lng == 0.0) return

    val obsJsonArray = JSONArray()
    obstacles.forEach { obs ->
        val item = JSONObject().apply {
            put("id", obs.id)
            put("lat", obs.latitude)
            put("lng", obs.longitude)
            put("type", obs.type)
            put("title", obs.title)
            put("isAuto", obs.isAutoDetected)
            put("strikes", obs.strikeCount)
            put("isFake", obs.isSuppressedFake)
        }
        obsJsonArray.put(item)
    }

    val js = "if (window.updateVehicleAndObstacles) { window.updateVehicleAndObstacles($lat, $lng, $heading, $alertDistance, $obsJsonArray); }"
    webView.evaluateJavascript(js, null)
}

private fun generateLeafletHtml(initLat: Double, initLng: Double): String {
    val lat = if (initLat != 0.0) initLat else 37.7749
    val lng = if (initLng != 0.0) initLng else -122.4194

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
            <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
            <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
            <style>
                body, html, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #0b0f19; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; }
                .leaflet-container { background: #0b0f19 !important; }
                .car-marker {
                    transform-origin: center center;
                    transition: transform 0.2s linear;
                }
                .custom-popup .leaflet-popup-content-wrapper {
                    background: #1e293b;
                    color: #f8fafc;
                    border-radius: 8px;
                    border: 1px solid #334155;
                }
                .custom-popup .leaflet-popup-tip {
                    background: #1e293b;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                var map = L.map('map', {
                    zoomControl: false,
                    attributionControl: false
                }).setView([$lat, $lng], 16);

                L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
                    maxZoom: 19
                }).addTo(map);

                L.control.zoom({ position: 'bottomright' }).addTo(map);

                var vehicleIcon = L.divIcon({
                    className: 'vehicle-div-icon',
                    html: '<div id="carIcon" style="width:36px;height:36px;display:flex;align-items:center;justify-content:center;background:#0284c7;border:3px solid #38bdf8;border-radius:50%;box-shadow:0 0 16px rgba(56,189,248,0.7);transform:rotate(0deg);"><svg width="20" height="20" viewBox="0 0 24 24" fill="white"><path d="M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z"/></svg></div>',
                    iconSize: [36, 36],
                    iconAnchor: [18, 18]
                });

                var vehicleMarker = L.marker([$lat, $lng], { icon: vehicleIcon }).addTo(map);
                var alertCircle = L.circle([$lat, $lng], {
                    radius: 50,
                    color: '#38bdf8',
                    fillColor: '#0284c7',
                    fillOpacity: 0.15,
                    weight: 2
                }).addTo(map);

                var obstacleMarkersLayer = L.layerGroup().addTo(map);

                map.on('click', function(e) {
                    if (window.AndroidBridge) {
                        window.AndroidBridge.onMapClicked(e.latlng.lat, e.latlng.lng);
                    }
                });

                window.updateVehicleAndObstacles = function(lat, lng, heading, alertDistance, obstacles) {
                    var newLatLng = new L.LatLng(lat, lng);
                    vehicleMarker.setLatLng(newLatLng);
                    alertCircle.setLatLng(newLatLng);
                    alertCircle.setRadius(alertDistance);

                    var carDiv = document.getElementById('carIcon');
                    if (carDiv) {
                        carDiv.style.transform = 'rotate(' + heading + 'deg)';
                    }

                    obstacleMarkersLayer.clearLayers();

                    if (obstacles && obstacles.length > 0) {
                        for (var i = 0; i < obstacles.length; i++) {
                            var obs = obstacles[i];
                            var color = obs.type === 'SPEED_BUMP' ? '#f59e0b' : '#ef4444';
                            var iconSvg = obs.type === 'SPEED_BUMP' ?
                                '<svg width="18" height="18" viewBox="0 0 24 24" fill="white"><path d="M4 14c2.5-4 5.5-4 8 0 2.5-4 5.5-4 8 0v2H4v-2z"/></svg>' :
                                '<svg width="18" height="18" viewBox="0 0 24 24" fill="white"><path d="M12 2a4 4 0 0 0-4 4v12a4 4 0 0 0 8 0V6a4 4 0 0 0-4-4zm0 3a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm0 5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3zm0 5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3z"/></svg>';

                            var badgeBorder = obs.isFake ? '#64748b' : color;
                            var badgeBg = obs.isFake ? '#334155' : color;
                            var opacity = obs.isFake ? '0.4' : '1.0';

                            var obsIcon = L.divIcon({
                                className: 'obs-icon',
                                html: '<div style="width:30px;height:30px;display:flex;align-items:center;justify-content:center;background:' + badgeBg + ';border:2px solid #ffffff;border-radius:8px;box-shadow:0 2px 8px rgba(0,0,0,0.5);opacity:' + opacity + ';">' + iconSvg + '</div>',
                                iconSize: [30, 30],
                                iconAnchor: [15, 15]
                            });

                            var marker = L.marker([obs.lat, obs.lng], { icon: obsIcon });
                            var popupText = '<div style="font-size:13px;font-weight:bold;">' + (obs.title || obs.type) + '</div>' +
                                '<div style="font-size:11px;color:#94a3b8;margin-top:2px;">Type: ' + obs.type + '</div>';
                            if (obs.isAuto) {
                                popupText += '<div style="font-size:11px;color:#38bdf8;">✓ Detected via Car Accelerometer</div>';
                            }
                            if (obs.strikes > 0) {
                                popupText += '<div style="font-size:11px;color:#f87171;">Unfelt strikes: ' + obs.strikes + (obs.isFake ? ' (Auto-Suppressed Fake)' : '') + '</div>';
                            }

                            marker.bindPopup(popupText, { className: 'custom-popup' });
                            obstacleMarkersLayer.addLayer(marker);
                        }
                    }
                };
            </script>
        </body>
        </html>
    """.trimIndent()
}
