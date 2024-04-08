package de.dennisguse.opentracks.stats;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.stats.TrackStatistics;
import de.dennisguse.opentracks.stats.TrackStatisticsUpdater;


import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

public class SkiStatistics {
    private LocalTime startTime;
    private LocalTime endTime;
    private long waitingTimeInSeconds;


    public SkiStatistics(LocalTime startTime, LocalTime endTime, long waitingTimeInSeconds) {
        this.startTime = startTime;
        this.endTime = endTime;
        this.waitingTimeInSeconds = waitingTimeInSeconds;
    }


    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public long getWaitingTimeInSeconds() {
        return waitingTimeInSeconds;
    }


    public void setWaitingTimeInSeconds(long waitingTimeInSeconds) {
        this.waitingTimeInSeconds = waitingTimeInSeconds;
    }
}

