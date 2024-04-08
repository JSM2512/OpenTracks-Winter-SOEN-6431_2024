package de.dennisguse.opentracks.stats;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.dennisguse.opentracks.data.models.AltitudeGainLoss;
import de.dennisguse.opentracks.data.models.TrackPoint;

public class SplitSegment {
    public static Map<String, List<TrackPoint>> splitTrackSegments(List<TrackPoint> trackPoints) {
        Map<String, List<TrackPoint>> segments = new HashMap<>();
        segments.put("Skiing", new ArrayList<>());
        segments.put("Chairlift", new ArrayList<>());
        segments.put("Waiting", new ArrayList<>());
        AltitudeGainLoss altGainLoss = new AltitudeGainLoss(0, 0);

        for (int i = 1; i < trackPoints.size(); i++) {
            TrackPoint currentPoint = trackPoints.get(i);
            boolean startNewSegment = altGainLoss.shouldStartNewSegment(trackPoints, i);

            if (startNewSegment) {
                addToSegment(segments, currentPoint, altGainLoss);
            } else {
                addToSegment(segments, currentPoint, altGainLoss);
            }
        }

        return segments;
    }

    private static void addToSegment(Map<String, List<TrackPoint>> segments, TrackPoint currentPoint, AltitudeGainLoss altGainLoss) {
        String segmentKey = determineSegmentKey(currentPoint,altGainLoss);
        segments.get(segmentKey).add(currentPoint);
    }

    static String determineSegmentKey(TrackPoint trackPoint, AltitudeGainLoss altGainLoss) {
        if (altGainLoss.isSkiing()) {
            return "Skiing";
        } else if (altGainLoss.isChairlift()) {
            return "Chairlift";
        } else {
            return "Waiting";
        }
    }

    public static int calculateWaitingTime(List<TrackPoint> trackPoints) {
        Duration waitingDuration = Duration.ZERO;
        boolean inChairlift = false;
        Instant startOfChairlift = null;
        AltitudeGainLoss altGainLoss = new AltitudeGainLoss(0, 0);

        for (int i = 0; i < trackPoints.size(); i++) {
            TrackPoint trackPoint = trackPoints.get(i);
            boolean startNewSegment = altGainLoss.shouldStartNewSegment(trackPoints, i);

            if (startNewSegment) {
                String currentSegmentKey = determineSegmentKey(trackPoint, altGainLoss);

                if (currentSegmentKey.equals("Chairlift")) {
                    if (!inChairlift) {
                        inChairlift = true;
                        startOfChairlift = trackPoint.getTime();
                    }
                } else {
                    if (inChairlift) {
                        inChairlift = false;
                        waitingDuration = waitingDuration.plus(Duration.between(startOfChairlift, trackPoint.getTime()));
                    }
                }

                if (i == trackPoints.size() - 1 && inChairlift) {
                    waitingDuration = waitingDuration.plus(Duration.between(startOfChairlift, Instant.now()));
                }
            }
        }

        return (int) waitingDuration.getSeconds();
    }

}
