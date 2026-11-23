package com.pace.app.tracking

import com.pace.app.data.local.entity.TrackPointEntity
import com.pace.app.domain.metrics.ActivityMetrics

/** Create completed 1 km splits, excluding elapsed gaps between resumed GPS segments. */
fun buildDistanceSplits(activityId: String, points: List<TrackPointEntity>) =
    ActivityMetrics.completedSplits(activityId, points)
