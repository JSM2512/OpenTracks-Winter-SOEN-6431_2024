/*
 * Copyright 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.dennisguse.opentracks.chart;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewParent;
import android.widget.Scroller;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Iterator;

import de.dennisguse.opentracks.R;
import de.dennisguse.opentracks.data.models.Marker;
import de.dennisguse.opentracks.settings.UnitSystem;
import de.dennisguse.opentracks.stats.ExtremityMonitor;
import de.dennisguse.opentracks.ui.markers.MarkerDetailActivity;
import de.dennisguse.opentracks.ui.markers.MarkerUtils;
import de.dennisguse.opentracks.ui.util.ThemeUtils;
import de.dennisguse.opentracks.util.IntentUtils;
import de.dennisguse.opentracks.util.StringUtils;

/**
 * Visualization of the chart.
 * Provides support for zooming (via pinch), scrolling, flinging, and selecting shown markers (single touch).
 *
 * @author Sandor Dornbush
 * @author Leif Hendrik Wilden
 */
public class ChartView extends View {

    private static final int Y_AXIS_INTERVALS = 5;

    private static final int X_AXIS_INTERVALS = 4;

    private static final int MIN_ZOOM_LEVEL = 1;
    private static final int MAX_ZOOM_LEVEL = 10;

    private static final NumberFormat X_NUMBER_FORMAT = NumberFormat.getIntegerInstance();
    private static final NumberFormat X_FRACTION_FORMAT = NumberFormat.getNumberInstance();
    private static final int BORDER = 8;
    private static final int SPACER = 4;
    private static final int Y_AXIS_OFFSET = 16;

    //TODO Determine from actual size of the used drawable
    private static final float MARKER_X_ANCHOR = 13f / 48f;

    static {
        X_FRACTION_FORMAT.setMaximumFractionDigits(1);
        X_FRACTION_FORMAT.setMinimumFractionDigits(1);
    }

    private final List<ChartValueSeries> seriesList = new LinkedList<>();
    private final ChartValueSeries speedSeries;
    private final ChartValueSeries paceSeries;

    private final LinkedList<ChartPoint> chartPoints = new LinkedList<>();
    private final List<Marker> markers = new LinkedList<>();
    private final ExtremityMonitor xExtremityMonitor = new ExtremityMonitor();
    private final int backgroundColor;
    private final Paint axisPaint;
    private final Paint xAxisMarkerPaint;
    private final Paint gridPaint;
    private final Paint markerPaint;
    private final Drawable pointer;
    private final Drawable markerPin;
    private final int markerWidth;
    private final int markerHeight;
    private final Scroller scroller;
    private double maxX = 1.0;
    private int zoomLevel = 1;

    private int leftBorder = BORDER;
    private int topBorder = BORDER;
    private int bottomBorder = BORDER;
    private int rightBorder = BORDER;
    private int getSpacer = SPACER;
    private int yAxisOffset = Y_AXIS_OFFSET;

    private int width = 0;
    private int height = 0;
    private int effectiveWidth = 0;
    private int effectiveHeight = 0;

    private boolean chartByDistance = false;
    private UnitSystem unitSystem = UnitSystem.defaultUnitSystem();
    private boolean reportSpeed = true;
    private boolean showPointer = false;

    private final GestureDetectorCompat scrollFlingTab = new GestureDetectorCompat(getContext(), new GestureDetector.SimpleOnGestureListener() {

        @Override
        public boolean onDown(final MotionEvent motionEvent) {
            if (!scroller.isFinished()) {
                scroller.abortAnimation();
            }
            return true;
        }

        @Override
        public boolean onScroll(final MotionEvent motionEvent1, final MotionEvent motionEvent2, final float distanceX, final float distanceY) {
            if (Math.abs(distanceX) > 0) {
                final int availableToScroll = effectiveWidth * (zoomLevel - 1) - getScrollX();
                if (availableToScroll > 0) {
                    scrollBy(Math.min(availableToScroll, (int) distanceX));
                }
            }
            return true;
        }

        @Override
        public boolean onFling(final MotionEvent motionEvent1,final  MotionEvent motionEvent2, final float velocityX,final  float velocityY) {
            fling((int) -velocityX);
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(final MotionEvent event) {
            boolean isTabComfirmed = false;
            // Check if the y event is within markerHeight of the marker center
            if (Math.abs(event.getY() - topBorder - getSpacer - markerHeight / 2f) < markerHeight) {
                int minDistance = Integer.MAX_VALUE;
                Marker nearestMarker = null;
                synchronized (markers) {
                    for (final Marker marker : markers) {
                        final int distance = Math.abs(getX(getMarkerXValue(marker)) - (int) event.getX() - getScrollX());
                        if (distance < minDistance) {
                            minDistance = distance;
                            nearestMarker = marker;
                        }
                    }
                }
                if (nearestMarker != null && minDistance < markerWidth) {
                    final Intent intent = IntentUtils.newIntent(getContext(), MarkerDetailActivity.class)
                            .putExtra(MarkerDetailActivity.EXTRA_MARKER_ID, nearestMarker.getId());
                    getContext().startActivity(intent);
                    isTabComfirmed = true;
                }
            }
            return isTabComfirmed;
        }
    });

    private final ScaleGestureDetector detectorZoom = new ScaleGestureDetector(getContext(), new ScaleGestureDetector.SimpleOnScaleGestureListener() {

        @Override
        public boolean onScale(final ScaleGestureDetector detector) {
            boolean isOnScale = false;
            final float scaleFactor = detector.getScaleFactor();
            if (scaleFactor >= 1.1f) {
                zoomIn();
                isOnScale = true;
            } else if (scaleFactor <= 0.9) {
                zoomOut();
                isOnScale = true;
            }
            return isOnScale;
        }
    });

    public ChartView(final Context context, final AttributeSet attributeSet) {
        super(context, attributeSet);

        final int fontSizeSmall = ThemeUtils.getFontSizeSmallInPx(context);
        final int fontSizeMedium = ThemeUtils.getFontSizeMediumInPx(context);

        seriesList.add(new ChartValueSeries(context,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                new int[]{5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000},
                R.string.description_altitude_metric,
                R.string.description_altitude_imperial,
                R.string.description_altitude_imperial,
                R.color.chart_altitude_fill,
                R.color.chart_altitude_border,
                fontSizeSmall,
                fontSizeMedium) {
            @Override
            protected Double extractDataFromChartPoint(final @NonNull ChartPoint chartPoint) {
                return chartPoint.altitude();
            }

            @Override
            protected boolean drawIfChartPointHasNoData() {
                return false;
            }
        });

        speedSeries = new ChartValueSeries(context,
                0,
                Integer.MAX_VALUE,
                new int[]{1, 5, 10, 20, 50, 100},
                R.string.description_speed_metric,
                R.string.description_speed_imperial,
                R.string.description_speed_nautical,
                R.color.chart_speed_fill,
                R.color.chart_speed_border,
                fontSizeSmall,
                fontSizeMedium) {
            @Override
            protected Double extractDataFromChartPoint(final @NonNull ChartPoint chartPoint) {
                return chartPoint.speed();
            }

            @Override
            protected boolean drawIfChartPointHasNoData() {
                return reportSpeed;
            }
        };
        seriesList.add(speedSeries);

        paceSeries = new ChartValueSeries(context,
                0,
                Integer.MAX_VALUE,
                new int[]{1, 2, 5, 10, 15, 20, 30, 60, 120},
                R.string.description_pace_metric,
                R.string.description_pace_imperial,
                R.string.description_pace_nautical,
                R.color.chart_pace_fill,
                R.color.chart_pace_border,
                fontSizeSmall,
                fontSizeMedium) {
            @Override
            protected Double extractDataFromChartPoint(final @NonNull ChartPoint chartPoint) {
                return chartPoint.pace();
            }

            @Override
            protected boolean drawIfChartPointHasNoData() {
                return !reportSpeed;
            }
        };
        seriesList.add(paceSeries);

        seriesList.add(new ChartValueSeries(context,
                0,
                Integer.MAX_VALUE,
                new int[]{25, 50},
                R.string.description_sensor_heart_rate,
                R.string.description_sensor_heart_rate,
                R.string.description_sensor_heart_rate,
                R.color.chart_heart_rate_fill,
                R.color.chart_heart_rate_border,
                fontSizeSmall,
                fontSizeMedium) {
            @Override
            protected Double extractDataFromChartPoint(final @NonNull ChartPoint chartPoint) {
                return chartPoint.heartRate();
            }

            @Override
            protected boolean drawIfChartPointHasNoData() {
                return false;
            }
        });

        seriesList.add(new ChartValueSeries(context,
                0,
                Integer.MAX_VALUE,
                new int[]{5, 10, 25, 50},
                R.string.description_sensor_cadence,
                R.string.description_sensor_cadence,
                R.string.description_sensor_cadence,
                R.color.chart_cadence_fill,
                R.color.chart_cadence_border,
                fontSizeSmall,
                fontSizeMedium) {
            @Override
            protected Double extractDataFromChartPoint(final @NonNull ChartPoint chartPoint) {
                return chartPoint.cadence();
            }

            @Override
            protected boolean drawIfChartPointHasNoData() {
                return false;
            }
        });
        seriesList.add(new ChartValueSeries(context,
                0,
                1000,
                new int[]{5, 50, 100, 200},
                R.string.description_sensor_power,
                R.string.description_sensor_power,
                R.string.description_sensor_power,
                R.color.chart_power_fill,
                R.color.chart_power_border,
                fontSizeSmall,
                fontSizeMedium) {
            @Override
            protected Double extractDataFromChartPoint(final @NonNull ChartPoint chartPoint) {
                return chartPoint.power();
            }

            @Override
            protected boolean drawIfChartPointHasNoData() {
                return false;
            }
        });

        backgroundColor = ThemeUtils.getBackgroundColor(context);

        axisPaint = new Paint();
        axisPaint.setStyle(Style.FILL_AND_STROKE);
        axisPaint.setColor(ThemeUtils.getTextColorPrimary(context));
        axisPaint.setAntiAlias(true);
        axisPaint.setTextSize(fontSizeSmall);

        xAxisMarkerPaint = new Paint(axisPaint);
        xAxisMarkerPaint.setTextAlign(Align.CENTER);

        gridPaint = new Paint();
        gridPaint.setStyle(Style.STROKE);
        gridPaint.setColor(ThemeUtils.getTextColorSecondary(context));
        gridPaint.setAntiAlias(false);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{3, 2}, 0));

        markerPaint = new Paint();
        markerPaint.setStyle(Style.STROKE);
        markerPaint.setAntiAlias(false);

        pointer = ContextCompat.getDrawable(context, R.drawable.ic_logo_color_24dp);
        pointer.setBounds(0, 0, pointer.getIntrinsicWidth(), pointer.getIntrinsicHeight());

        markerPin = MarkerUtils.getDefaultPhoto(context);
        markerWidth = markerPin.getIntrinsicWidth();
        markerHeight = markerPin.getIntrinsicHeight();
        markerPin.setBounds(0, 0, markerWidth, markerHeight);

        scroller = new Scroller(context);
        setFocusable(true);
        setClickable(true);
        updateDimensions();

        // either speedSeries or paceSeries should be enabled.
        speedSeries.setEnabled(reportSpeed);
        paceSeries.setEnabled(!reportSpeed);
    }

    @Override
    public boolean canScrollHorizontally(final int direction) {
        return true;
    }

    public void setChartByDistance(final boolean chartByDistance) {
        this.chartByDistance = chartByDistance;
    }

    public UnitSystem getUnitSystem() {
        return unitSystem;
    }

    public void setUnitSystem(final UnitSystem value) {
        unitSystem = value;
    }

    public boolean isReportSpeed() {
        return reportSpeed;
    }

    /**
     * Sets report speed.
     *
     * @param value report speed (true) or pace (false)
     */
    public void setReportSpeed(final boolean value) {
        reportSpeed = value;
    }

    public boolean applyReportSpeed() {
        boolean isApplied = false;
        if (reportSpeed) {
            if (!speedSeries.isEnabled()) {
                speedSeries.setEnabled(true);
                paceSeries.setEnabled(false);
                isApplied = true;
            }
        } else {
            if (!paceSeries.isEnabled()) {
                speedSeries.setEnabled(false);
                paceSeries.setEnabled(true);
                isApplied = true;
            }
        }

        return isApplied;
    }

    public void setShowPointer(final boolean value) {
        showPointer = value;
    }

    public void addChartPoints(final List<ChartPoint> dataPoints) {
        synchronized (chartPoints) {
            chartPoints.addAll(dataPoints);
            for (final ChartPoint dataPoint : dataPoints) {
                xExtremityMonitor.update(dataPoint.timeOrDistance());
                for (final ChartValueSeries i : seriesList) {
                    i.update(dataPoint);
                }
            }
            updateDimensions();
            updateSeries();
        }
    }

    /**
     * Clears all data.
     */
    public void reset() {
        synchronized (chartPoints) {
            chartPoints.clear();
            xExtremityMonitor.reset();
            zoomLevel = 1;
            updateDimensions();
        }
    }

    /**
     * Resets scroll.
     * To be called on the UI thread.
     */
    public void resetScroll() {
        scrollTo(0, 0);
    }

    public void addMarker(final Marker marker) {
        synchronized (markers) {
            markers.add(marker);
        }
    }

    public void clearMarker() {
        synchronized (markers) {
            markers.clear();
        }
    }

    private boolean canZoomIn() {
        return zoomLevel < MAX_ZOOM_LEVEL;
    }

    private boolean canZoomOut() {
        return zoomLevel > MIN_ZOOM_LEVEL;
    }

    private void zoomIn() {
        if (canZoomIn()) {
            zoomLevel++;
            updateSeries();
            invalidate();
        }
    }

    private void zoomOut() {
        if (canZoomOut()) {
            zoomLevel--;
            scroller.abortAnimation();
            int scrollX = getScrollX();
            final int maxWidth = effectiveWidth * (zoomLevel - 1);
            if (scrollX > maxWidth) {
                scrollX = maxWidth;
                scrollTo(scrollX, 0);
            }
            updateSeries();
            invalidate();
        }
    }

    /**
     * Initiates flinging.
     *
     * @param velocityX velocity of fling in pixels per second
     */
    private void fling(final int velocityX) {
        final int maxWidth = effectiveWidth * (zoomLevel - 1);
        scroller.fling(getScrollX(), 0, velocityX, 0, 0, maxWidth, 0, 0);
        invalidate();
    }

    /**
     * Handle parent's view disallow touch event.
     *
     * @param disallow Does disallow parent touch event?
     */
    private void requestDisallowInterceptTouchEventInParent(final boolean disallow) {
        final ViewParent parent = getParent();
        if (parent != null) {
            parent.requestDisallowInterceptTouchEvent(disallow);
        }
    }

    /**
     * Scrolls the view horizontally by a given amount.
     *
     * @param deltaX the number of pixels to scroll
     */
    private void scrollBy(final int deltaX) {
        int scrollX = getScrollX() + deltaX;
        if (scrollX <= 0) {
            scrollX = 0;
        }

        final int maxWidth = effectiveWidth * (zoomLevel - 1);
        if (scrollX >= maxWidth) {
            scrollX = maxWidth;
        }

        scrollTo(scrollX, 0);
    }

    /**
     * Called by the parent to indicate that the mScrollX/Y values need to be
     * updated. Triggers a redraw during flinging.
     */
    @Override
    public void computeScroll() {
        if (scroller.computeScrollOffset()) {
            final int oldX = getScrollX();
            final int newX = scroller.getCurrX();
            scrollTo(newX, 0);
            if (oldX != newX) {
                onScrollChanged(newX, 0, oldX, 0);
                postInvalidate();
            }
        }
    }

    @Override
    public boolean onTouchEvent(final MotionEvent event) {
        final boolean isZoom = detectorZoom.onTouchEvent(event);
        final boolean isScrollTab = scrollFlingTab.onTouchEvent(event);

        // ChartView handles zoom gestures (more than one pointer) and all gestures when zoomed itself
        requestDisallowInterceptTouchEventInParent(event.getPointerCount() != 1 || zoomLevel != MIN_ZOOM_LEVEL);

        return isZoom || isScrollTab;
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        updateEffectiveDimensionsIfChanged(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(heightMeasureSpec));
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        synchronized (chartPoints) {
            canvas.save();

            canvas.drawColor(backgroundColor);

            canvas.save();

            clipToGraphArea(canvas);
            drawDataSeries(canvas);
            drawMarker(canvas);
            drawGrid(canvas);

            canvas.restore();

            drawSeriesTitles(canvas);
            drawXAxis(canvas);
            drawYAxis(canvas);

            canvas.restore();

            if (showPointer) {
                drawPointer(canvas);
            }
        }
    }

    /**
     * Clips a canvas to the graph area.
     *
     * @param canvas the canvas
     */
    private void clipToGraphArea(final Canvas canvas) {
        final int xCordinate = getScrollX() + leftBorder;
        final int yCordinate = topBorder;
        canvas.clipRect(xCordinate, yCordinate, xCordinate + effectiveWidth, yCordinate + effectiveHeight);
    }

    /**
     * Draws the data series.
     *
     * @param canvas the canvas
     */
    private void drawDataSeries(final Canvas canvas) {
        for (final ChartValueSeries chartValueSeries : seriesList) {
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData()) {
                chartValueSeries.drawPath(canvas);
            }
        }
    }

    private void drawMarker(final Canvas canvas) {
        synchronized (markers) {
            for (final Marker marker : markers) {
                final double xValue = getMarkerXValue(marker);
                final double markerSizeXaxis = maxX*markerWidth/effectiveWidth / zoomLevel;
                if (xValue > maxX + markerSizeXaxis * (1-MARKER_X_ANCHOR)) {
                    continue; // there is no chance that this marker will be visible
                }
                canvas.save();
                final float xCordinate = getX(getMarkerXValue(marker));
                canvas.drawLine(xCordinate, topBorder + getSpacer + markerHeight / 2, xCordinate, topBorder + effectiveHeight, markerPaint);
                // if marker is not near the end of the track then draw it normally
                if (xValue < maxX - markerSizeXaxis*(1-MARKER_X_ANCHOR)) {
                    canvas.translate(xCordinate - (markerWidth * MARKER_X_ANCHOR), topBorder + getSpacer);
                } else { // marker at the end needs to be drawn mirrored so that it is more visible
                    canvas.translate(xCordinate + (markerWidth * MARKER_X_ANCHOR), topBorder + getSpacer);
                    canvas.scale(-1, 1);
                }
                markerPin.draw(canvas);
                canvas.restore();
            }
        }
    }

    /**
     * Draws the grid.
     *
     * @param canvas the canvas
     */
    private void drawGrid(final Canvas canvas) {
        // X axis grid
        final List<Double> xMarkerPositions = getXAxisMarkerPositions(getXAxisInterval());
        for (final double position : xMarkerPositions) {
            final int xCordinate = getX(position);
            canvas.drawLine(xCordinate, topBorder, xCordinate, topBorder + effectiveHeight, gridPaint);
        }
        // Y axis grid
        final float rightEdge = getX(maxX);
        for (int i = 0; i <= Y_AXIS_INTERVALS; i++) {
            final double percentage = (double) i / Y_AXIS_INTERVALS;
            final int range = effectiveHeight - 2 * yAxisOffset;
            final int yCordinate = topBorder + yAxisOffset + (int) (percentage * range);
            canvas.drawLine(leftBorder, yCordinate, rightEdge, yCordinate, gridPaint);
        }
    }

    private record TitlePosition(
            int line, // line number (starts at 1, top to bottom numbering)
            int xPos // x position in points (starts at 0, left to right indexing)
    ) {}
    private record TitleDimensions(
            int lineCount, // number of lines the titles will take
            int lineHeight, // height of a line (all lines have the same height)
            List<TitlePosition> titlePositions // positions of visible titles (the order corresponds to seriesList)
    ) {}

    /**
     * Draws series titles.
     *
     * @param canvas the canvas
     */
    private void drawSeriesTitles(final Canvas canvas) {
        final TitleDimensions titleDimensions = getTitleDimensions();
        final Iterator<TitlePosition> tpI = titleDimensions.titlePositions.iterator();
        for (final ChartValueSeries chartValueSeries : seriesList) {
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() || allowIfEmpty(chartValueSeries)) {
                final String title = getContext().getString(chartValueSeries.getTitleId(unitSystem));
                final Paint paint = chartValueSeries.getTitlePaint();
                final TitlePosition titlePosition = tpI.next();
                final int yCordinate = topBorder - getSpacer - (titleDimensions.lineCount - titlePosition.line) * (titleDimensions.lineHeight + getSpacer);
                canvas.drawText(title, titlePosition.xPos + getScrollX(), yCordinate, paint);
            }
        }
    }

    /**
     * Gets the title dimensions.
     * Returns an array of 2 integers, first element is the number of lines and the second element is the line height.
     */
    private TitleDimensions getTitleDimensions() {
        int lineCnt = 1;
        int lineHeight = 0;
        final List<TitlePosition> tps = new ArrayList();
        int xPosInLine = getSpacer;
        for (final ChartValueSeries chartValueSeries : seriesList) {
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() || allowIfEmpty(chartValueSeries)) {
                final String title = getContext().getString(chartValueSeries.getTitleId(unitSystem));
                final Rect rect = getRect(chartValueSeries.getTitlePaint(), title);
                if (rect.height() > lineHeight) {
                    lineHeight = rect.height();
                }
                final int xNextPosInLine = xPosInLine + rect.width() + 2*getSpacer;
                // if second or later title does not fully fit on this line then print it on the next line
                if (xPosInLine > getSpacer && xNextPosInLine-getSpacer > width) {
                    lineCnt++;
                    xPosInLine = getSpacer;
                }
                tps.add(new TitlePosition(lineCnt, xPosInLine));
                xPosInLine += rect.width() + 2*getSpacer;
            }
        }
        return new TitleDimensions(lineCnt, lineHeight, tps);
    }

    /**
     * Draws the x axis.
     *
     * @param canvas the canvas
     */
    private void drawXAxis(final Canvas canvas) {
        final int xCordinate = getScrollX() + leftBorder;
        final int yCordinate = topBorder + effectiveHeight;
        canvas.drawLine(xCordinate, yCordinate, xCordinate + effectiveWidth, yCordinate, axisPaint);
        final String label = getXAxisLabel();
        final Rect rect = getRect(axisPaint, label);
        final int yOffset = rect.height() / 2;
        canvas.drawText(label, xCordinate + effectiveWidth - rect.width(), yCordinate + 3 * yOffset, axisPaint);

        final double interval = getXAxisInterval();
        final NumberFormat numberFormat = interval < 1 ? X_FRACTION_FORMAT : X_NUMBER_FORMAT;

        for (final double markerPosition : getXAxisMarkerPositions(interval)) {
            drawXAxisMarker(canvas, markerPosition, numberFormat, getSpacer + rect.width(), getSpacer + yOffset);
        }
    }

    private String getXAxisLabel() {
        final Context context = getContext();
        if (chartByDistance) {
            return switch (unitSystem) {
                case METRIC -> context.getString(R.string.unit_kilometer);
                case IMPERIAL_FEET, IMPERIAL_METER -> context.getString(R.string.unit_mile);
                case NAUTICAL_IMPERIAL -> context.getString(R.string.unit_nautical_mile);
            };
        } else {
            return context.getString(R.string.description_time);
        }
    }

    /**
     * Draws a x axis marker.
     *
     * @param canvas       canvas
     * @param value        value
     * @param numberFormat the number format
     * @param xRightSpace  the space taken up by the x axis label
     * @param yBottomSpace the space between x axis and marker
     */
    private void drawXAxisMarker(final Canvas canvas, final double value,final  NumberFormat numberFormat,final  int xRightSpace, final int yBottomSpace) {
        final String marker = chartByDistance ? numberFormat.format(value) : StringUtils.formatElapsedTime(Duration.ofMillis((long) value));
        final Rect rect = getRect(xAxisMarkerPaint, marker);
        final int markerXPos = getX(value);
        final int markerEndXPos = markerXPos + rect.width()/2;
        if (markerEndXPos > getScrollX() + leftBorder + effectiveWidth - xRightSpace){
            return;
        }
        canvas.drawText(marker, markerXPos, topBorder + effectiveHeight + yBottomSpace + rect.height(), xAxisMarkerPaint);
    }

    private double getXAxisInterval() {
        double interval = maxX / zoomLevel / X_AXIS_INTERVALS;
        if (interval < 1) {
            interval = .5;
        } else if (interval < 5) {
            interval = 2;
        } else if (interval < 10) {
            interval = 5;
        } else {
            interval = (interval / 10) * 10;
        }
        return interval;
    }

    private List<Double> getXAxisMarkerPositions(final double interval) {
        final List<Double> xAxisMarkers = new ArrayList<>();
        xAxisMarkers.add(0d);
        for (int i = 1; i * interval < maxX; i++) {
            xAxisMarkers.add(i * interval);
        }

        if (xAxisMarkers.size() < 2) {
            xAxisMarkers.add(maxX);
        }
        return xAxisMarkers;
    }

    /**
     * Draws the y axis.
     *
     * @param canvas the canvas
     */
    private void drawYAxis(final Canvas canvas) {
        final int xCordinate = getScrollX() + leftBorder;
        final int yCordinate = topBorder;
        canvas.drawLine(xCordinate, yCordinate, xCordinate, yCordinate + effectiveHeight, axisPaint);

        //TODO
        int markerXPosition = xCordinate - getSpacer;
        for (int i = 0; i < seriesList.size(); i++) {
            final int index = seriesList.size() - 1 - i;
            final ChartValueSeries chartValueSeries = seriesList.get(index);
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() || allowIfEmpty(chartValueSeries)) {
                markerXPosition -= drawYAxisMarkers(chartValueSeries, canvas, markerXPosition) + getSpacer;
            }
        }
    }

    /**
     * Draws the y axis markers for a chart value series.
     *
     * @param chartValueSeries the chart value series
     * @param canvas           the canvas
     * @param xPosition        the right most x position
     * @return the maximum marker width.
     */
    private float drawYAxisMarkers(final ChartValueSeries chartValueSeries, final Canvas canvas, final int xPosition) {
        final int interval = chartValueSeries.getInterval();
        float maxMarkerWidth = 0;
        for (int i = 0; i <= Y_AXIS_INTERVALS; i++) {
            maxMarkerWidth = Math.max(maxMarkerWidth, drawYAxisMarker(chartValueSeries, canvas, xPosition,
                    i * interval + chartValueSeries.getMinMarkerValue()));
        }
        return maxMarkerWidth;
    }

    /**
     * Draws a y axis marker.
     *
     * @param chartValueSeries the chart value series
     * @param canvas           the canvas
     * @param xPosition        the right most x position
     * @param yValue           the y value
     * @return the marker width.
     */
    private float drawYAxisMarker(final ChartValueSeries chartValueSeries, final Canvas canvas, final int xPosition,final  int yValue) {
        final String marker = chartValueSeries.formatMarker(yValue);
        final Paint paint = chartValueSeries.getMarkerPaint();
        final Rect rect = getRect(paint, marker);
        final int yPosition = getY(chartValueSeries, yValue) + (rect.height() / 2);
        canvas.drawText(marker, xPosition, yPosition, paint);
        return paint.measureText(marker);
    }

    private void drawPointer(final Canvas canvas) {
        if (chartPoints.isEmpty()) {
            return;
        }
        final ChartPoint last = chartPoints.getLast();

        ChartValueSeries firstChartSeries = null;
        for (final ChartValueSeries chartValueSeries : seriesList) {
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() && chartValueSeries.isChartPointValid(last)) {
                firstChartSeries = chartValueSeries;
                break;
            }
        }
        if (firstChartSeries != null && chartPoints.isEmpty()) {
            final int drawXCordinate = getX(maxX) - pointer.getIntrinsicWidth() / 2;
            final double value = firstChartSeries.extractDataFromChartPoint(last);
            final int drawYCordinate = getY(firstChartSeries, value) - pointer.getIntrinsicHeight();
            canvas.translate(drawXCordinate, drawYCordinate);
            pointer.draw(canvas);
        }
    }

    /**
     * The path needs to be updated any time after the data or the dimensions change.
     */
    private void updateSeries() {
        synchronized (chartPoints) {
            seriesList.stream().forEach(this::updateSerie);
        }
    }

    private void updateSerie(final ChartValueSeries series) {
        final int yCorner = topBorder + effectiveHeight;
        final Path path = series.getPath();

        boolean drawFirstPoint = false;
        path.rewind();

        Integer finalX = null;

        for (final ChartPoint point : chartPoints) {
            if (!series.isChartPointValid(point)) {
                continue;
            }

            final double value = series.extractDataFromChartPoint(point);
            final int xCordinate = getX(point.timeOrDistance());
            final int yCordinate = getY(series, value);

            // start from lower left corner
            if (!drawFirstPoint) {
                path.moveTo(xCordinate, yCorner);
                drawFirstPoint = true;
            }

            // draw graph
            path.lineTo(xCordinate, yCordinate);

            finalX = xCordinate;
        }

        // last point: move to lower right
        if (finalX != null) {
            path.lineTo(finalX, yCorner);
        }

        // back to lower left corner
        path.close();
    }

    /**
     * Updates the chart dimensions.
     */
    private void updateDimensions() {
        maxX = xExtremityMonitor.hasData() ? xExtremityMonitor.getMax() : 1.0;
        for (final ChartValueSeries chartValueSeries : seriesList) {
            chartValueSeries.updateDimension();
        }
        final float density = getResources().getDisplayMetrics().density;
        getSpacer = (int) (density * SPACER);
        yAxisOffset = (int) (density * Y_AXIS_OFFSET);

        int markerLength = 0;
        for (final ChartValueSeries chartValueSeries : seriesList) {
            if (chartValueSeries.isEnabled() && chartValueSeries.hasData() || allowIfEmpty(chartValueSeries)) {
                final Rect rect = getRect(chartValueSeries.getMarkerPaint(), chartValueSeries.getLargestMarker());
                markerLength += rect.width() + getSpacer;
            }
        }

        leftBorder = (int) (density * BORDER + markerLength);
        final TitleDimensions titleDimensions = getTitleDimensions();
        topBorder = (int) (density * BORDER + titleDimensions.lineCount * (titleDimensions.lineHeight + getSpacer));
        final Rect xAxisLabelRect = getRect(axisPaint, getXAxisLabel());
        // border + x axis marker + getSpacer + .5 x axis label
        bottomBorder = (int) (density * BORDER + getRect(xAxisMarkerPaint, "1").height() + getSpacer + (xAxisLabelRect.height() / 2));
        rightBorder = (int) (density * BORDER + getSpacer);
        updateEffectiveDimensions();
    }

    /**
     * Updates the effective dimensions.
     */
    private void updateEffectiveDimensions() {
        effectiveWidth = Math.max(0, width - leftBorder - rightBorder);
        effectiveHeight = Math.max(0, height - topBorder - bottomBorder - getSpacer);
    }

    /**
     * Updates the effective dimensions if changed.
     *
     * @param newWidth  the new width
     * @param newHeight the new height
     */
    private void updateEffectiveDimensionsIfChanged(final int newWidth, final int newHeight) {
        if (width != newWidth || height != newHeight) {
            width = newWidth;
            height = newHeight;
            updateEffectiveDimensions();
            updateSeries();
        }
    }

    /**
     * Gets the x position for a value.
     *
     * @param value the value
     */
    private int getX(double value) {
        if (value > maxX) {
            value = maxX;
        }
        final double percentage = value / maxX;
        return leftBorder + (int) (percentage * effectiveWidth * zoomLevel);
    }

    /**
     * Gets the y position for a value in a chart value series
     *
     * @param chartValueSeries the chart value series
     * @param value            the value
     */
    private int getY(final ChartValueSeries chartValueSeries,final  double value) {
        final int effectiveSpread = chartValueSeries.getInterval() * Y_AXIS_INTERVALS;
        final double percentage = (value - chartValueSeries.getMinMarkerValue()) / effectiveSpread;
        final int rangeHeight = effectiveHeight - 2 * yAxisOffset;
        return topBorder + yAxisOffset + (int) ((1 - percentage) * rangeHeight);
    }

    private double getMarkerXValue(final Marker marker) {
        int xCordinate = marker.getDuration().toMillis();
        if (chartByDistance) {
            xCordinate = marker.getLength().toKM_Miles(unitSystem);
        }
        return xCordinate;
    }

    /**
     * Gets a paint's Rect for a string.
     *
     * @param paint  the paint
     * @param string the string
     */
    private Rect getRect(final Paint paint,final  String string) {
        final Rect rect = new Rect();
        paint.getTextBounds(string, 0, string.length(), rect);
        return rect;
    }

    /**
     * Returns true if the index is allowed when the chartData is empty.
     */
    private boolean allowIfEmpty(final ChartValueSeries chartValueSeries) {
        boolean isAllowed = chartValueSeries.drawIfChartPointHasNoData();
        if (!chartPoints.isEmpty()) {
            isAllowed = false;
        }
        return isAllowed;
    }
}
