package de.dennisguse.opentracks.stats;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.stats.TrackStatistics;
import de.dennisguse.opentracks.stats.TrackStatisticsUpdater;

import java.time.Duration;
import java.time.LocalTime;

public class SkiStatisticsCalculator {

    public static long calculateTotalTravelTime(SkiStatistics stats) {
        return Duration.between(stats.getStartTime(), stats.getEndTime()).getSeconds();
    }

    public static String getTimeOfDay(SkiStatistics stats) {
        LocalTime startTime = LocalTime.from(stats.getStartTime());
        if (startTime.isBefore(LocalTime.of(12, 0))) {
            return "morning";
        } else if (startTime.isBefore(LocalTime.of(18, 0))) {
            return "afternoon";
        } else {
            return "evening";
        }
    }

    public static void main(String[] args) {
        LocalTime startTime = LocalTime.of(9, 30);
        LocalTime endTime = LocalTime.of(15, 45);
        long waitingTimeInSeconds = 300;

        SkiStatistics stats = new SkiStatistics(startTime, endTime, waitingTimeInSeconds);

        long totalTravelTime = calculateTotalTravelTime(stats);
        String timeOfDay = getTimeOfDay(stats);

        System.out.println("Total Travel Time: " + totalTravelTime + " seconds");
        System.out.println("Time of the Day: " + timeOfDay);
        System.out.println("Waiting Time: " + stats.getWaitingTimeInSeconds() + " seconds");
    }
}
