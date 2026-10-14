package app.quartz.core.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import app.quartz.core.ui.R

/** One Material Symbols Rounded icon: outlined (FILL 0) and filled (FILL 1). */
data class MsIcon(@DrawableRes val outlined: Int, @DrawableRes val filled: Int)

/**
 * Material Symbols Rounded (Apache-2.0) used by Quartz, generated from
 * tools/icons.txt by tools/icons.py. Filled = selected / "on", as in Material.
 */
object Symbols {
    val photoCamera = MsIcon(R.drawable.ms_photo_camera, R.drawable.ms_photo_camera_fill)
    val videocam = MsIcon(R.drawable.ms_videocam, R.drawable.ms_videocam_fill)
    val cameraswitch = MsIcon(R.drawable.ms_cameraswitch, R.drawable.ms_cameraswitch_fill)
    val settings = MsIcon(R.drawable.ms_settings, R.drawable.ms_settings_fill)
    val flashOn = MsIcon(R.drawable.ms_flash_on, R.drawable.ms_flash_on_fill)
    val flashOff = MsIcon(R.drawable.ms_flash_off, R.drawable.ms_flash_off_fill)
    val flashAuto = MsIcon(R.drawable.ms_flash_auto, R.drawable.ms_flash_auto_fill)
    val timer = MsIcon(R.drawable.ms_timer, R.drawable.ms_timer_fill)
    val timerOff = MsIcon(R.drawable.ms_timer_off, R.drawable.ms_timer_off_fill)
    val timer3 = MsIcon(R.drawable.ms_timer_3, R.drawable.ms_timer_3_fill)
    val timer10 = MsIcon(R.drawable.ms_timer_10, R.drawable.ms_timer_10_fill)
    val aspectRatio = MsIcon(R.drawable.ms_aspect_ratio, R.drawable.ms_aspect_ratio_fill)
    val gridOn = MsIcon(R.drawable.ms_grid_on, R.drawable.ms_grid_on_fill)
    val gridOff = MsIcon(R.drawable.ms_grid_off, R.drawable.ms_grid_off_fill)
    val straighten = MsIcon(R.drawable.ms_straighten, R.drawable.ms_straighten_fill)
    val rawOn = MsIcon(R.drawable.ms_raw_on, R.drawable.ms_raw_on_fill)
    val rawOff = MsIcon(R.drawable.ms_raw_off, R.drawable.ms_raw_off_fill)
    val hdrOn = MsIcon(R.drawable.ms_hdr_on, R.drawable.ms_hdr_on_fill)
    val hdrOff = MsIcon(R.drawable.ms_hdr_off, R.drawable.ms_hdr_off_fill)
    val slowMotionVideo = MsIcon(R.drawable.ms_slow_motion_video, R.drawable.ms_slow_motion_video_fill)
    val timelapse = MsIcon(R.drawable.ms_timelapse, R.drawable.ms_timelapse_fill)
    val shutterSpeed = MsIcon(R.drawable.ms_shutter_speed, R.drawable.ms_shutter_speed_fill)
    val exposureZero = MsIcon(R.drawable.ms_exposure_zero, R.drawable.ms_exposure_zero_fill)
    val exposure = MsIcon(R.drawable.ms_exposure, R.drawable.ms_exposure_fill)
    val wbAuto = MsIcon(R.drawable.ms_wb_auto, R.drawable.ms_wb_auto_fill)
    val wbIncandescent = MsIcon(R.drawable.ms_wb_incandescent, R.drawable.ms_wb_incandescent_fill)
    val wbIridescent = MsIcon(R.drawable.ms_wb_iridescent, R.drawable.ms_wb_iridescent_fill)
    val wbSunny = MsIcon(R.drawable.ms_wb_sunny, R.drawable.ms_wb_sunny_fill)
    val filterDrama = MsIcon(R.drawable.ms_filter_drama, R.drawable.ms_filter_drama_fill)
    val wbShade = MsIcon(R.drawable.ms_wb_shade, R.drawable.ms_wb_shade_fill)
    val filterCenterFocus = MsIcon(R.drawable.ms_filter_center_focus, R.drawable.ms_filter_center_focus_fill)
    val centerFocusStrong = MsIcon(R.drawable.ms_center_focus_strong, R.drawable.ms_center_focus_strong_fill)
    val centerFocusWeak = MsIcon(R.drawable.ms_center_focus_weak, R.drawable.ms_center_focus_weak_fill)
    val lens = MsIcon(R.drawable.ms_lens, R.drawable.ms_lens_fill)
    val pause = MsIcon(R.drawable.ms_pause, R.drawable.ms_pause_fill)
    val playArrow = MsIcon(R.drawable.ms_play_arrow, R.drawable.ms_play_arrow_fill)
    val stop = MsIcon(R.drawable.ms_stop, R.drawable.ms_stop_fill)
    val mic = MsIcon(R.drawable.ms_mic, R.drawable.ms_mic_fill)
    val micOff = MsIcon(R.drawable.ms_mic_off, R.drawable.ms_mic_off_fill)
    val locationOn = MsIcon(R.drawable.ms_location_on, R.drawable.ms_location_on_fill)
    val locationOff = MsIcon(R.drawable.ms_location_off, R.drawable.ms_location_off_fill)
    val photoLibrary = MsIcon(R.drawable.ms_photo_library, R.drawable.ms_photo_library_fill)
    val share = MsIcon(R.drawable.ms_share, R.drawable.ms_share_fill)
    val delete = MsIcon(R.drawable.ms_delete, R.drawable.ms_delete_fill)
    val info = MsIcon(R.drawable.ms_info, R.drawable.ms_info_fill)
    val barChart = MsIcon(R.drawable.ms_bar_chart, R.drawable.ms_bar_chart_fill)
    val thermostat = MsIcon(R.drawable.ms_thermostat, R.drawable.ms_thermostat_fill)
    val sdCard = MsIcon(R.drawable.ms_sd_card, R.drawable.ms_sd_card_fill)
    val lock = MsIcon(R.drawable.ms_lock, R.drawable.ms_lock_fill)
    val lockOpen = MsIcon(R.drawable.ms_lock_open, R.drawable.ms_lock_open_fill)
    val close = MsIcon(R.drawable.ms_close, R.drawable.ms_close_fill)
    val check = MsIcon(R.drawable.ms_check, R.drawable.ms_check_fill)
    val arrowBack = MsIcon(R.drawable.ms_arrow_back, R.drawable.ms_arrow_back_fill)
    val chevronRight = MsIcon(R.drawable.ms_chevron_right, R.drawable.ms_chevron_right_fill)
    val tune = MsIcon(R.drawable.ms_tune, R.drawable.ms_tune_fill)
    val burstMode = MsIcon(R.drawable.ms_burst_mode, R.drawable.ms_burst_mode_fill)
    val schedule = MsIcon(R.drawable.ms_schedule, R.drawable.ms_schedule_fill)
    val speed = MsIcon(R.drawable.ms_speed, R.drawable.ms_speed_fill)
    val graphicEq = MsIcon(R.drawable.ms_graphic_eq, R.drawable.ms_graphic_eq_fill)
    val photoSizeSelectLarge = MsIcon(R.drawable.ms_photo_size_select_large, R.drawable.ms_photo_size_select_large_fill)
    val cameraFront = MsIcon(R.drawable.ms_camera_front, R.drawable.ms_camera_front_fill)
    val cameraRear = MsIcon(R.drawable.ms_camera_rear, R.drawable.ms_camera_rear_fill)
    val zoomIn = MsIcon(R.drawable.ms_zoom_in, R.drawable.ms_zoom_in_fill)
    val verified = MsIcon(R.drawable.ms_verified, R.drawable.ms_verified_fill)
    val warning = MsIcon(R.drawable.ms_warning, R.drawable.ms_warning_fill)
    val help = MsIcon(R.drawable.ms_help, R.drawable.ms_help_fill)
    val iosShare = MsIcon(R.drawable.ms_ios_share, R.drawable.ms_ios_share_fill)
    val moreVert = MsIcon(R.drawable.ms_more_vert, R.drawable.ms_more_vert_fill)
    val movie = MsIcon(R.drawable.ms_movie, R.drawable.ms_movie_fill)
    val photo = MsIcon(R.drawable.ms_photo, R.drawable.ms_photo_fill)
    val gradient = MsIcon(R.drawable.ms_gradient, R.drawable.ms_gradient_fill)
    val volumeUp = MsIcon(R.drawable.ms_volume_up, R.drawable.ms_volume_up_fill)
    val vibration = MsIcon(R.drawable.ms_vibration, R.drawable.ms_vibration_fill)
    val landscape = MsIcon(R.drawable.ms_landscape, R.drawable.ms_landscape_fill)
    val darkMode = MsIcon(R.drawable.ms_dark_mode, R.drawable.ms_dark_mode_fill)
    val openInNew = MsIcon(R.drawable.ms_open_in_new, R.drawable.ms_open_in_new_fill)
    val image = MsIcon(R.drawable.ms_image, R.drawable.ms_image_fill)
}

@Composable
fun SymbolIcon(
    icon: MsIcon,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    tint: Color = LocalContentColor.current,
) {
    Icon(
        painter = painterResource(if (filled) icon.filled else icon.outlined),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}
