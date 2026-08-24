package com.sient.mytimer

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Chip
import androidx.wear.protolayout.material.Colors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

private const val RESOURCES_VERSION = "1"
private const val ID_ICON_TIMER = "icon_timer"
private const val ID_ICON_RUN = "icon_run"

/**
 * "My Apps" tile: quick-launch chips for MyTimer and MyRun.
 * Tiles may only launch activities in their own package, so the Run chip
 * goes through [LaunchRunActivity], which forwards to MyRun.
 */
class MyAppsTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val deviceParameters = requestParams.deviceConfiguration
        val layout = LayoutElementBuilders.Layout.Builder()
            .setRoot(tileLayout(deviceParameters))
            .build()
        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(layout)
                            .build()
                    )
                    .build()
            )
            .build()
        return Futures.immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder()
                .setVersion(RESOURCES_VERSION)
                .addIdToImageMapping(ID_ICON_TIMER, imageResource(R.drawable.ic_timer_notification))
                .addIdToImageMapping(ID_ICON_RUN, imageResource(R.drawable.ic_run))
                .build()
        )

    private fun imageResource(resId: Int): ResourceBuilders.ImageResource =
        ResourceBuilders.ImageResource.Builder()
            .setAndroidResourceByResId(
                ResourceBuilders.AndroidImageResourceByResId.Builder()
                    .setResourceId(resId)
                    .build()
            )
            .build()

    private fun tileLayout(deviceParameters: DeviceParameters): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setWidth(DimensionBuilders.expand())
            .addContent(
                appChip("Timer", ID_ICON_TIMER, "timer", MainActivityClass, deviceParameters)
            )
            .addContent(
                LayoutElementBuilders.Spacer.Builder()
                    .setHeight(DimensionBuilders.dp(8f))
                    .build()
            )
            .addContent(
                appChip("Run", ID_ICON_RUN, "run", LaunchRunActivityClass, deviceParameters)
            )
            .build()

        return PrimaryLayout.Builder(deviceParameters)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "My Apps")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(argb(Colors.DEFAULT.onSurface))
                    .build()
            )
            .setContent(column)
            .build()
    }

    private fun appChip(
        label: String,
        iconId: String,
        clickId: String,
        activityClass: String,
        deviceParameters: DeviceParameters,
    ): Chip {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId(clickId)
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName(packageName)
                            .setClassName(activityClass)
                            .build()
                    )
                    .build()
            )
            .build()
        return Chip.Builder(this, clickable, deviceParameters)
            .setPrimaryLabelContent(label)
            .setIconContent(iconId)
            .setWidth(DimensionBuilders.expand())
            .build()
    }

    private companion object {
        const val MainActivityClass = "com.sient.mytimer.presentation.MainActivity"
        const val LaunchRunActivityClass = "com.sient.mytimer.LaunchRunActivity"
    }
}
