package io.github.ardalorn.lufoss

/**
 * Every pause, timeout, retry, limit and URL in one place, ported from the settings block of the ArWalk page.
 * Times are in milliseconds, distances in metres, angles in degrees, sizes in dp unless a name says otherwise.
 */
object Cfg {
    // ---- Networking and caches ----
    const val USER_AGENT = "Lufoss/1.0 (Android walking navigation; https://ardalorn.github.io/ArTools/)"
    const val FETCH_TIMEOUT_MS = 15_000L
    const val FETCH_TRIES = 2
    const val FETCH_RETRY_MS = 2_000L
    const val CACHE_OK_MS = 1_800_000L
    const val CACHE_MISS_MS = 120_000L
    const val CACHE_MAX_ENTRIES = 250
    const val CACHE_TRIM_TO = 200
    const val HTTP_CACHE_BYTES = 50L * 1024L * 1024L

    // ---- Map ----
    const val TILE = 256
    const val MIN_Z = 2.0
    const val MAX_TILE_Z = 19
    const val MAX_VIEW_Z = 20.0
    const val MAX_LAT = 85.0511
    const val START_LAT = 51.5074
    const val START_LNG = -0.1278
    const val START_Z = 12.0
    const val TILE_CACHE_MAX = 300 // Tiles are decoded as RGB_565 (128 KB each), so this is about 40 MB.
    const val TILE_MEMORY_TTL_MS = 900_000L
    const val TILE_PARALLEL = 6
    const val TILE_RETRY_AFTER_MS = 5_000L
    const val TILE_URL = "https://tile.openstreetmap.org/"

    // ---- Gestures ----
    const val TAP_MOVE_DP = 8f
    const val TAP_MS = 400L
    const val DOUBLE_TAP_MS = 280L
    const val WHEEL_ZOOM_PER_NOTCH = 0.25
    const val KEY_PAN_DP = 100.0
    const val FLY_MS = 450L
    const val EASE_POWER = 3.0
    const val ZOOM_STEP = 1.0
    const val PLACE_ZOOM = 16.0
    const val ROTATE_START_DEG = 8.0
    const val ROTATE_KEY_DEG = 15.0
    const val ROTATE_DRAG_DEG_PER_PX = 0.4
    const val NORTH_SNAP_DEG = 3.0
    const val COMPASS_SHOW_DEG = 0.5

    // ---- Drawing ----
    const val ROUTE_W = 7f
    const val ROUTE_CASING = 4f
    const val PIN_HEAD_OFFSET = 26f
    const val PIN_HEAD_R = 13f
    const val PIN_HOLE_R = 4.5f
    const val DOT_RING = 3f
    const val ME_DOT_R = 8f
    const val START_DOT_R = 8f
    const val ACCURACY_MIN_R = 12.0
    const val CONE_R = 38f
    const val CONE_HALF_DEG = 32f
    const val CONE_ALPHA = 0.3f
    const val EARTH_CIRCUMFERENCE_M = 40_075_016.686

    // ---- Fitting a route into the free part of the screen ----
    const val FIT_PAD_TOP = 90.0
    const val FIT_PAD_RIGHT = 70.0
    const val FIT_PAD_BOTTOM = 60.0
    const val FIT_PAD_LEFT = 30.0
    const val FIT_PAD_SHEET_SIDE = 50.0
    const val FIT_MIN_AREA = 60.0
    const val FIT_MAX_Z = 18.0
    const val EPSILON = 1e-9

    // ---- Search ----
    const val SEARCH_LIMIT = 6
    const val SEARCH_FETCH_LIMIT = 20
    const val ADDR_MAX = 90
    const val SUGGEST_MIN = 3
    const val SUGGEST_DEBOUNCE_MS = 350L
    const val MIN_SUBMIT_CHARS = 2
    const val STREET_CANDIDATES = 3
    const val NOMINATIM_URL = "https://nominatim.openstreetmap.org/search"
    const val NOMINATIM_TIMEOUT_MS = 8_000L
    const val OVERPASS_URL = "https://overpass-api.de/api/interpreter"
    const val OVERPASS_AROUND_M = 1500
    const val OVERPASS_TIMEOUT_MS = 12_000L
    const val GEOCODE_URL = "https://photon.komoot.io/api/"
    const val REVERSE_URL = "https://photon.komoot.io/reverse"
    const val REVERSE_TIMEOUT_MS = 6_000L
    const val REVERSE_NOMINATIM_URL = "https://nominatim.openstreetmap.org/reverse"
    const val REVERSE_NOMINATIM_TIMEOUT_MS = 8_000L
    const val ROUTE_URL = "https://routing.openstreetmap.de/routed-foot/route/v1/foot/"

    // ---- Location ----
    const val GOOD_ACC_M = 30.0
    const val REFINE_MAX_MS = 20_000L
    const val MAX_FIX_AGE_MS = 10_000L
    const val MAX_NAV_ACC_M = 100.0

    // ---- Turn by turn ----
    const val NAV_ZOOM = 17.0
    const val NAV_ZOOM_MID = 17.5
    const val NAV_ZOOM_NEAR = 18.0
    const val ZOOM_MID_M = 150.0
    const val ZOOM_NEAR_M = 60.0
    const val REROUTE_M = 40.0
    const val REROUTE_MIN_MS = 10_000L
    const val ARRIVE_M = 20.0
    const val SIM_STEP_M = 14.0
    const val SIM_TICK_MS = 1_000L
    const val ANNOUNCE_FAR_M = 200.0
    const val ANNOUNCE_NEAR_M = 40.0
    const val SNAP_BACK_M = 30.0
    const val STAGE_FAR = 0
    const val STAGE_NEAR = 1
    const val STAGE_TURN = 2
    const val TURN_STRAIGHT_DEG = 20.0
    const val TURN_SLIGHT_DEG = 60.0
    const val TURN_SHARP_DEG = 130.0
    const val TURN_UTURN_DEG = 160.0

    // ---- Follow camera ----
    const val NAV_ANCHOR = 0.68
    const val ANCHOR_MARGIN = 12.0
    const val ATTRIB_HEIGHT = 32.0
    const val ME_SMOOTH_MS = 350.0
    const val ZOOM_SMOOTH_MS = 450.0
    const val BEARING_SMOOTH_MS = 250.0
    const val FOLLOW_ENGAGE_MS = 600L
    const val MAX_FRAME_MS = 100L
    const val POS_DONE_DEG = 1e-7
    const val ZOOM_DONE = 0.005
    const val BEARING_DONE = 0.2
    const val ANCHOR_DONE = 0.002
    const val AUTO_RECENTRE_MS = 8_000L

    // ---- Heading ----
    const val SRC_NONE = 0
    const val SRC_ROUTE = 1
    const val SRC_COMPASS = 2
    const val SRC_GPS = 3
    const val MOVING_SPEED_MS = 0.8
    const val MOVE_BEARING_M = 4.0
    const val HEADING_HOLD_MS = 4_000L
    const val HEADING_DEADBAND_DEG = 2.0
    const val GAIN_GPS = 0.5
    const val GAIN_COMPASS = 0.2
    const val GAIN_ROUTE = 0.3
    const val FLAT_TILT_COS = 0.7071 // Phone is "held flat" when its screen faces up within 45 degrees.
    const val ROUTE_LOOKAHEAD_M = 15.0

    // ---- Round-trip walks ----
    val loopMinutes = listOf(10, 20, 30, 40, 50, 60)
    const val LOOP_SPEED_MS = 1.3
    const val LOOP_DETOUR = 1.35
    const val LOOP_SIDES = 3
    const val LOOP_CORNER_DEG = 60.0
    const val LOOP_TRIES = 6
    const val LOOP_BEARING_STEP_DEG = 60.0
    const val LOOP_REFINES = 4
    const val LOOP_TOL = 0.025
    const val LOOP_CLEAN_OVERLAP = 0.01
    const val LOOP_OVERLAP_MIN_M = 150.0
    const val LOOP_START_ZONE_M = 30.0
    const val LOOP_UTURN_ARM_M = 20.0 // How far each way along the route to look when checking for a U-turn.
    const val LOOP_UTURN_FOLD_DEG = 180.0 - TURN_UTURN_DEG // Both arms within this angle means the line folded back.
    const val LOOP_CELL_DEG = 0.0002
    const val LOOP_SCALE_MIN = 0.5
    const val LOOP_SCALE_MAX = 2.0
    const val LOOP_FAIL_SHRINK = 0.7
    const val LOOP_BUDGET_MS = 45_000L
    const val LOOP_FETCH_TIMEOUT_MS = 10_000L
    const val LOOP_AHEAD_M = 300.0
    const val LOOP_WP_PASSED_M = 30.0

    // ---- Layout ----
    const val WIDE_LAYOUT_DP = 896
}
