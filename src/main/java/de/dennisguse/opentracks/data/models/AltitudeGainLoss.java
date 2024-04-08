package de.dennisguse.opentracks.data.models;

import java.util.List;

public record AltitudeGainLoss(float gain_m, float loss_m) {

    private static final double ALT_CHANGE_THRESHOLD = 10.0;

    private static boolean isSkiing;
    private static boolean isChairlift;

    public AltitudeGainLoss(float gain_m, float loss_m) {
        this.gain_m = gain_m;
        this.loss_m = loss_m;
    }

    public static boolean isSkiing(){
        return isSkiing;
    }
    public static boolean isChairlift(){
        return isChairlift;
    }

    // Determines if a new segment should start based on altitude change thresholds.
    public boolean shouldStartNewSegment(List<TrackPoint> trackPoints, int currentIndex) {

        TrackPoint current = trackPoints.get(currentIndex);
        TrackPoint previous = trackPoints.get(currentIndex - 1);

        Altitude currentAltitude = current.getAltitude();
        Altitude previousAltitude = previous.getAltitude();

        if (currentAltitude != null && previousAltitude != null) {
            double altitudeChange = currentAltitude.toM() - previousAltitude.toM();

            return shouldStartChairlift(altitudeChange) || shouldStartSkiing(altitudeChange);
        }
        return false;
    }

    private boolean shouldStartChairlift(double altitudeChange) {
        if (altitudeChange > ALT_CHANGE_THRESHOLD) {
            isChairlift = true;
            isSkiing = false;
            return true;
        }
        return false;
    }
    private boolean shouldStartSkiing(double altitudeChange) {
        if (altitudeChange < ALT_CHANGE_THRESHOLD) {
            isChairlift = false;
            isSkiing = true;
            return true;
        }
        return false;
    }

}
