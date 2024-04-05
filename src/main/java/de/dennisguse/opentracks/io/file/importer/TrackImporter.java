package de.dennisguse.opentracks.io.file.importer;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.models.ActivityType;
import de.dennisguse.opentracks.data.models.Distance;
import de.dennisguse.opentracks.data.models.Marker;
import de.dennisguse.opentracks.data.models.Speed;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.stats.TrackStatistics;
import de.dennisguse.opentracks.stats.TrackStatisticsUpdater;
import de.dennisguse.opentracks.ui.markers.MarkerUtils;
import de.dennisguse.opentracks.util.FileUtils;
import de.dennisguse.opentracks.util.LocationUtils;

/**
 * Handles logic to import:
 * 1. addTrackPoints()
 * 2. addMarkers();
 * 3. setTrack();
 * 4. newTrack(); //stores current track to databse
 * 5. if needed go to 1.
 * 6. finish()
 * <p>
 * NOTE: This class modifies the parameter.
 * Do not re-use these objects anywhere else.
 */
public class TrackImporter {

    private static final String TAG = TrackImporter.class.getSimpleName();

    private final Context context;
    private final ContentProviderUtils contentProviderUtils;

    private final Distance maxRecordingDistance;
    private final boolean preventReimport;

    private final List<Track.Id> trackIds = new ArrayList<>();

    // Current track
    private Track track;
    private final List<TrackPoint> trackPoints = new LinkedList<>();
    private final List<Marker> markers = new LinkedList<>();

    public TrackImporter(Context context, ContentProviderUtils contentProviderUtils, Distance maxRecordingDistance, boolean preventReimport) {
        this.context = context;
        this.contentProviderUtils = contentProviderUtils;
        this.maxRecordingDistance = maxRecordingDistance;
        this.preventReimport = preventReimport;
    }

    void newTrack() {
        if (track != null) {
            finishTrack();
        }

        track = null;
        trackPoints.clear();
        markers.clear();
    }

    void addTrackPoint(TrackPoint trackPoint) {
        this.trackPoints.add(trackPoint);
    }

    void addTrackPoints(List<TrackPoint> trackPoints) {
        this.trackPoints.addAll(trackPoints);
    }

    void addMarkers(List<Marker> markers) {
        this.markers.addAll(markers);
    }

    void setTrack(Context context, String name, String uuid, String description, String activityTypeLocalized, String activityTypeId, @Nullable ZoneOffset zoneOffset) {
        track = new Track(zoneOffset != null ? zoneOffset : ZoneOffset.UTC);
        track.setName(name != null ? name : "");

        try {
            track.setUuid(UUID.fromString(uuid));
        } catch (IllegalArgumentException | NullPointerException e) {
            Log.w(TAG, "could not parse Track UUID, generating a new one.");
            track.setUuid(UUID.randomUUID());
        }

        track.setDescription(description != null ? description : "");

        if (activityTypeLocalized != null) {
            track.setActivityTypeLocalized(activityTypeLocalized);
        }
        ActivityType activityType;
        if (activityTypeId == null) {
            activityType = ActivityType.findByLocalizedString(context, activityTypeLocalized);
        } else {
            activityType = ActivityType.findBy(activityTypeId);
        }
        track.setActivityType(activityType);
    }

    void finish() {
        if (track != null) {
            finishTrack();
        }
    }

    private void finishTrack() {
        if (trackPoints.isEmpty()) {
            throw new ImportParserException("Cannot import track without any locations.");
        }

        // Store Track
        if (contentProviderUtils.getTrack(track.getUuid()) != null) {
            if (preventReimport) {
                throw new ImportAlreadyExistsException(context.getString(R.string.import_prevent_reimport));
            }

            //TODO This is a workaround until we have proper UI.
            track.setUuid(UUID.randomUUID());
        }

        trackPoints.sort((o1, o2) -> {
            if (o1.getTime().isBefore(o2.getTime())) {
                return -1;
            }
            if (o1.getTime().isAfter(o2.getTime())) {
                return 1;
            }
            return 0;
        });

        adjustTrackPoints();

        TrackStatisticsUpdater updater = new TrackStatisticsUpdater();
        updater.addTrackPoints(trackPoints);
        track.setTrackStatistics(updater.getTrackStatistics());

        Track.Id trackId = contentProviderUtils.insertTrack(track);

        // Store TrackPoints
        contentProviderUtils.bulkInsertTrackPoint(trackPoints, trackId);

        // Store Markers
        matchMarkers2TrackPoints(trackId);
        for (Marker marker : markers)
            marker.setTrackId(trackId); //TODO Should happen in bulkInsertMarkers

        contentProviderUtils.bulkInsertMarkers(markers);

        //Clear up.
        trackPoints.clear();
        markers.clear();

        trackIds.add(trackId);
    }

    /**
     * If not present: calculate data from the previous trackPoint (if present)
     * NOTE: Modifies content of trackPoints.
     */
    private void adjustTrackPoints() {
        for (int i = 0; i < trackPoints.size(); i++) {
            TrackPoint current = trackPoints.get(i);

            if (current.hasLocation()) {
                Instant time = current.getTime();
                if (current.getLatitude() == 100) {
                    //TODO Remove by 31st December 2021.
                    trackPoints.set(i, new TrackPoint(TrackPoint.Type.SEGMENT_END_MANUAL, time));
                } else if (current.getLatitude() == 200) {
                    //TODO Remove by 31st December 2021.
                    trackPoints.set(i, new TrackPoint(TrackPoint.Type.SEGMENT_START_MANUAL, time));
                    //TODO Delete location
                } else if (!LocationUtils.isValidLocation(current.getLocation())) {
                    throw new ImportParserException("Invalid location detected: " + current);
                }
            }
        }

        for (int i = 1; i < trackPoints.size(); i++) {
            TrackPoint previous = trackPoints.get(i - 1);
            TrackPoint current = trackPoints.get(i);

            if (current.hasSensorDistance() || (previous.hasLocation() && current.hasLocation())) {
                Distance distanceToPrevious = current.distanceToPrevious(previous);
                if (!current.hasSpeed()) {
                    Duration timeDifference = Duration.between(previous.getTime(), current.getTime());
                    current.setSpeed(Speed.of(distanceToPrevious, timeDifference));
                }

                if (!current.hasBearing()) {
                    current.setBearing(previous.bearingTo(current));
                }

                if (current.getType().equals(TrackPoint.Type.TRACKPOINT) && distanceToPrevious.greaterThan(maxRecordingDistance)) {
                    current.setType(TrackPoint.Type.SEGMENT_START_AUTOMATIC);
                }
            }
        }
    }

    /**
     * NOTE: Modifies content of markers (incl. removal).
     */
    private void matchMarkers2TrackPoints(Track.Id trackId) {
        List<TrackPoint> trackPointsWithLocation = trackPoints.stream()
                .filter(TrackPoint::hasLocation)
                .collect(Collectors.toList());

        List<Marker> todoMarkers = new LinkedList<>(markers);
        List<Marker> doneMarkers = new LinkedList<>();

        for (final TrackPoint trackPoint : trackPointsWithLocation) {
            if (todoMarkers.isEmpty()) {
                break;
            }

            TrackStatisticsUpdater updater = new TrackStatisticsUpdater();
            updater.addTrackPoint(trackPoint);

            List<Marker> matchedMarkers = todoMarkers.stream()
                    .filter(it -> trackPoint.getLatitude() == it.getLatitude()
                            && trackPoint.getLongitude() == it.getLongitude()
                            && trackPoint.getTime().equals(it.getTime())
                    )
                    .collect(Collectors.toList());

            TrackStatistics statistics = updater.getTrackStatistics();
            for (Marker marker : matchedMarkers) {
                if (marker.hasPhoto()) {
                    marker.setPhotoUrl(getInternalPhotoUrl(trackId, marker.getPhotoUrl()));
                }

                marker.setIcon(context.getString(R.string.marker_icon_url)); //TODO Why?

                marker.setLength(statistics.getTotalDistance());
                marker.setDuration(statistics.getTotalTime());

                marker.setTrackPoint(trackPoint);
            }

            todoMarkers.removeAll(matchedMarkers);
            doneMarkers.addAll(matchedMarkers);
        }

        if (todoMarkers.isEmpty()) {
            Log.w(TAG, "Some markers could not be attached to TrackPoints; those are not imported.");
        }

        markers.clear();
        markers.addAll(doneMarkers);
    }

    /**
     * Gets the photo url for a file.
     *
     * @param externalPhotoUrl the file name
     */
    private String getInternalPhotoUrl(@NonNull Track.Id trackId, @NonNull String externalPhotoUrl) {
        String importFileName = KmzTrackImporter.importNameForFilename(externalPhotoUrl);
        File file = MarkerUtils.buildInternalPhotoFile(context, trackId, Uri.parse(importFileName));
        if (file != null) {
            Uri photoUri = FileUtils.getUriForFile(context, file);
            return "" + photoUri;
        }

        return null;
    }

    public List<Track.Id> getTrackIds() {
        return Collections.unmodifiableList(trackIds);
    }

    public void cleanImport() {
        contentProviderUtils.deleteTracks(context, trackIds);
    }

    /**
     * Finds the start time of the chair lift ride with respect to the provided acceleration threshold.
     *
     * @param trackPoints       The list of track points representing the ski lift trajectory
     * @param currentTrackPoint The current track point
     * @param acceleration      The acceleration threshold
     * @return                  The start time of the chair lift ride
     */
    public Instant findStartTimeWithAccelerationThreshold(List<TrackPoint> trackPoints, TrackPoint currentTrackPoint, double acceleration) {
        Instant startTime = currentTrackPoint.getTime(); // Initialize start time
        double altitudeDifference = -1; // Initialize altitude difference

        TrackPoint previousTrackPoint = trackPoints.get(trackPoints.size() - 1); // Get the last track point in the list

        for (int i = trackPoints.size() - 2; i > 0; i--) { // Iterate through track points in reverse order
            TrackPoint selectedTrackPoint = trackPoints.get(i);
            double currentAltitudeDifference = previousTrackPoint.getAltitude().toM() - selectedTrackPoint.getAltitude().toM();

            // Check if altitude difference is within the acceleration threshold
            if (altitudeDifference == -1 || Math.abs(currentAltitudeDifference - altitudeDifference) <= acceleration) {
                startTime = selectedTrackPoint.getTime(); // Update start time
                altitudeDifference = currentAltitudeDifference; // Update altitude difference
            }
        }
        return startTime;
    }

    /**
     * Calculates the total time spent on the chair lift ride based on the start time and current time.
     *
     * @param trackPoints       The list of track points representing the ski lift trajectory
     * @param currentTrackPoint The current track point
     * @param acceleration      The acceleration threshold
     * @return                  The total time spent on the chair lift ride in seconds
     */
    public double calculateTotalTimeOnChairLift(List<TrackPoint> trackPoints, TrackPoint currentTrackPoint, double acceleration) {
        Instant startTime = findStartTimeWithAccelerationThreshold(trackPoints, currentTrackPoint, acceleration);
        Instant endTime = currentTrackPoint.getTime();

        // Calculate total time on chair lift
        return Duration.between(startTime, endTime).getSeconds();
    }

    public boolean isEnteringChairlift(TrackPoint currentTrackPoint, double elevationThreshold) {
        List<TrackPoint> recentTrackPoints = filterRecentTrackPoints(currentTrackPoint);
        if (recentTrackPoints.isEmpty()) {
            return false;
        }
        double currentElevation = currentTrackPoint.getAltitude().toM();
        for (TrackPoint point : recentTrackPoints) {
            double elevation = point.getAltitude().toM();
            if (Math.abs(currentElevation - elevation) > elevationThreshold) {
                return false;
            }
        }
        return true;
    }
    private List<TrackPoint> filterRecentTrackPoints(TrackPoint currentTrackPoint) {
        List<TrackPoint> recentTrackPoints = new ArrayList<>();
        Instant currentTime = currentTrackPoint.getTime();
        for (TrackPoint point : trackPoints) {
            Instant pointTime = point.getTime();
            Duration timeDifference = Duration.between(pointTime, currentTime);
            if (!timeDifference.isNegative() && timeDifference.getSeconds() <= 20) {
                recentTrackPoints.add(point);
            }
        }
        return recentTrackPoints;
    }
}
