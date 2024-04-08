package de.dennisguse.opentracks.ui.aggregatedStatistics.daySpecificStats;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import de.dennisguse.opentracks.AbstractActivity;
import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.TrackListActivity;
import de.dennisguse.opentracks.data.ContentProviderUtils;
import de.dennisguse.opentracks.data.TrackPointIterator;
import de.dennisguse.opentracks.data.models.Track;
import de.dennisguse.opentracks.data.models.TrackPoint;
import de.dennisguse.opentracks.data.models.TrackSegment;
import de.dennisguse.opentracks.databinding.DaySpecificActivityBinding;

import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.Date;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;

public class DaySpecificActivity extends AbstractActivity {

    private DaySpecificActivityBinding viewBinding;
    private static final String TAG = DaySpecificActivity.class.getSimpleName();
    public static final String EXTRA_TRACK_DATE = "track_date";
    private Date activityDate;
    private ContentProviderUtils contentProviderUtils;
    private Track.Id trackId;
    private List<TrackSegment> trackSegments;
    private DaySpecificAdapter dataAdapter;

    private String fallbackDate = "2024-03-09";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        trackSegments = new ArrayList<>();
        contentProviderUtils = new ContentProviderUtils(this);
        handleIntent(getIntent());
        updateTrackSegments();
        setSupportActionBar(viewBinding.bottomAppBarLayout.bottomAppBar);

        dataAdapter = new DaySpecificAdapter(this, viewBinding.segmentList);
        dataAdapter.swapData(trackSegments);
        viewBinding.segmentList.setAdapter(dataAdapter);
        viewBinding.segmentListToolbar.setTitle(fallbackDate);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateTrackSegments();
        dataAdapter.swapData(trackSegments);
    }

    public void updateTrackSegments() {
        if (trackId == null) {
            return;
        }
        try (TrackPointIterator trackPointIterator = contentProviderUtils.getTrackPointLocationIterator(trackId, null)) {
            TrackSegment currentSegment = null;
            while (trackPointIterator.hasNext()) {
                TrackPoint nextPoint = trackPointIterator.next();

                switch (nextPoint.getType()) {
                    case SEGMENT_START_AUTOMATIC:
                    case SEGMENT_START_MANUAL:
                        if (currentSegment != null) {
                            trackSegments.add(currentSegment);
                        }
                        currentSegment = new TrackSegment(nextPoint.getTime());
                        break;

                    case SEGMENT_END_MANUAL:
                        trackSegments.add(currentSegment);
                        currentSegment = null;

                    case TRACKPOINT:
                        if (currentSegment != null) {
                            currentSegment.addTrackPoint(nextPoint);
                        }
                        break;

                    default:
                        Log.d(TAG, "No Action for TrackPoint IDLE/SENSORPOINT while recording segments");
                }
            }
        }
    }

    private void showNoTracksFoundToast() {
        finish();
        Toast.makeText(DaySpecificActivity.this, "No Tracks found for date: " + fallbackDate + "\n Please import GPX file from Moodle", Toast.LENGTH_LONG).show();
    }
    private Date getFallbackDate() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate localDate = LocalDate.parse(fallbackDate, formatter);
        return Date.from(localDate.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private void handleIntent(Intent intent) {
        Date dateFromCalendar = intent.getParcelableExtra(EXTRA_TRACK_DATE);
        if (dateFromCalendar == null) {
            Log.e(TAG, DaySpecificActivity.class.getSimpleName() + " needs EXTRA_TRACK_ID.");

            // None provided, we will assume a specific date on our own
            activityDate = getFallbackDate();
        } else {
            activityDate = dateFromCalendar;
        }

        Track track = contentProviderUtils.getTrack(activityDate);
        if (track == null) {
            showNoTracksFoundToast();
        } else {
            trackId = track.getId();
        }
    }

    @Override
    protected View getRootView() {
        viewBinding = DaySpecificActivityBinding.inflate(getLayoutInflater());
        return viewBinding.getRoot();
    }
}