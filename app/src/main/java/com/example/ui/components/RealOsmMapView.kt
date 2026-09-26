package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
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
    onMapReady: ((MapController) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val webView = remember {
        WebView(context).apply {
            // Fix: Use SOFTWARE layer to prevent Mesa from querying /dev/dri/renderD render nodes
            // in emulator / headless container environments
            try {
                setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            } catch (e: Exception) {
                Log.w("RealOsmMapView", "Could not set software layer: ${e.message}")
            }

            setBackgroundColor(android.graphics.Color.parseColor("#070a13"))

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                databaseEnabled = true
                
                // Disable safe browsing check to eliminate variations_seed_loader signature errors
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = false
                }
            }

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
                    onMapReady?.invoke(MapController(this@apply))
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    Log.w("RealOsmMapView", "WebViewClient error $errorCode: $description")
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Log.w("RealOsmMapView", "WebResourceError ${error?.errorCode}: ${error?.description}")
                    }
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

/**
 * Controller without floating +- or current location buttons
 */
class MapController(private val webView: WebView) {
    fun recenter(lat: Double, lng: Double) {
        webView.evaluateJavascript("if (window.recenterMap) { window.recenterMap($lat, $lng); }", null)
    }
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
                body, html, #map { margin: 0; padding: 0; width: 100%; height: 100%; background: #070a13; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; overflow: hidden; }
                .leaflet-container { background: #070a13 !important; }
                
                /* Requirement: Remove map +- current location button completely */
                .leaflet-control-zoom,
                .leaflet-control-zoom-in,
                .leaflet-control-zoom-out,
                .leaflet-control-attribution,
                .leaflet-control,
                .leaflet-bar,
                .leaflet-top,
                .leaflet-bottom,
                .leaflet-left,
                .leaflet-right {
                    display: none !important;
                    visibility: hidden !important;
                    opacity: 0 !important;
                    pointer-events: none !important;
                }
                
                .car-marker {
                    transform-origin: center center;
                    transition: transform 0.25s linear;
                }
                .custom-popup .leaflet-popup-content-wrapper {
                    background: #0f172a;
                    color: #f8fafc;
                    border-radius: 12px;
                    border: 1px solid #334155;
                    box-shadow: 0 4px 16px rgba(0,0,0,0.8);
                }
                .custom-popup .leaflet-popup-tip {
                    background: #0f172a;
                }
            </style>
        </head>
        <body>
            <div id="map"></div>
            <script>
                // Map initialized without zoom controls or location button
                var map = L.map('map', {
                    zoomControl: false,
                    attributionControl: false,
                    boxZoom: false,
                    doubleClickZoom: false,
                    scrollWheelZoom: true,
                    touchZoom: true
                }).setView([$lat, $lng], 16);

                // High contrast dark road tiles for gorgeous wallpaper aesthetic
                L.tileLayer('https://{s}.basemaps.cartocdn.com/rastertiles/voyager/{z}/{x}/{y}{r}.png', {
                    maxZoom: 19,
                    subdomains: 'abcd',
                    errorTileUrl: ''
                }).addTo(map);

                window.recenterMap = function(lat, lng) {
                    map.panTo([lat, lng], { animate: true, duration: 0.5 });
                };

                var vehicleIcon = L.divIcon({
                    className: 'vehicle-div-icon',
                    html: '<div id="carIcon" style="width:38px;height:38px;display:flex;align-items:center;justify-content:center;background:#0284c7;border:3px solid #38bdf8;border-radius:50%;box-shadow:0 0 18px rgba(56,189,248,0.9);transform:rotate(0deg);"><svg width="22" height="22" viewBox="0 0 24 24" fill="white"><path d="M12 2L4.5 20.29l.71.71L12 18l6.79 3 .71-.71z"/></svg></div>',
                    iconSize: [38, 38],
                    iconAnchor: [19, 19]
                });

                var vehicleMarker = L.marker([$lat, $lng], { icon: vehicleIcon }).addTo(map);
                var alertCircle = L.circle([$lat, $lng], {
                    radius: 50,
                    color: '#38bdf8',
                    fillColor: '#0284c7',
                    fillOpacity: 0.18,
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
                            var color = '#f59e0b';
                            var iconSvg = '';

                            if (obs.type === 'TRAFFIC_SIGNAL') {
                                color = '#ef4444';
                                iconSvg = '<svg width="20" height="20" viewBox="0 0 24 24" fill="white"><rect x="7" y="2" width="10" height="20" rx="3" fill="#1e293b" stroke="#ffffff" stroke-width="1.5"/><circle cx="12" cy="6" r="2.2" fill="#ef4444"/><circle cx="12" cy="12" r="2.2" fill="#f59e0b"/><circle cx="12" cy="18" r="2.2" fill="#10b981"/></svg>';
                            } else if (obs.type === 'BIG_GUTTER') {
                                color = '#0284c7';
                                iconSvg = '<svg width="20" height="20" viewBox="0 0 24 24" fill="white"><path d="M4 4h16v2H4zm2 4h12v2H6zm-2 4h16v2H4zm2 4h12v2H6zm-2 4h16v2H4z"/></svg>';
                            } else {
                                // SPEED_BUMP
                                color = '#f59e0b';
                                iconSvg = '<svg width="20" height="20" viewBox="0 0 24 24" fill="white"><path d="M3 16c3-6 6-6 9 0 3-6 6-6 9 0v2H3v-2z"/></svg>';
                            }

                            var badgeBorder = obs.isFake ? '#64748b' : '#ffffff';
                            var badgeBg = obs.isFake ? '#334155' : color;
                            var opacity = obs.isFake ? '0.35' : '1.0';

                            var obsIcon = L.divIcon({
                                className: 'obs-icon',
                                html: '<div style="width:34px;height:34px;display:flex;align-items:center;justify-content:center;background:' + badgeBg + ';border:2px solid ' + badgeBorder + ';border-radius:10px;box-shadow:0 3px 10px rgba(0,0,0,0.6);opacity:' + opacity + ';">' + iconSvg + '</div>',
                                iconSize: [34, 34],
                                iconAnchor: [17, 17]
                            });

                            var marker = L.marker([obs.lat, obs.lng], { icon: obsIcon });
                            var popupText = '<div style="font-size:13px;font-weight:bold;color:#f8fafc;">' + (obs.title || obs.type) + '</div>' +
                                '<div style="font-size:11px;color:#94a3b8;margin-top:2px;">Road Feature: ' + obs.type + '</div>';
                            if (obs.isAuto) {
                                popupText += '<div style="font-size:11px;color:#38bdf8;margin-top:2px;">✓ Verified by Vehicle Sensors</div>';
                            }
                            if (obs.strikes > 0) {
                                popupText += '<div style="font-size:11px;color:#f87171;margin-top:2px;">Unfelt strikes: ' + obs.strikes + (obs.isFake ? ' (Auto-Suppressed)' : '') + '</div>';
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
