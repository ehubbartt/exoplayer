package com.example.exoplayer;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.TracksInfo;
import com.google.android.exoplayer2.drm.DefaultDrmSessionManager;
import com.google.android.exoplayer2.drm.DrmSessionManager;
import com.google.android.exoplayer2.drm.FrameworkMediaDrm;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.ui.StyledPlayerControlView;
import com.google.android.exoplayer2.ui.StyledPlayerView;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource;
import com.google.android.exoplayer2.upstream.HttpDataSource;
import com.google.android.exoplayer2.util.Assertions;
import com.google.android.exoplayer2.util.Util;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, StyledPlayerControlView.VisibilityListener, Player.EventListener, Player.Listener {
    private ExoPlayer player;
    private Button selectTracksButton;
    private boolean isShowingTrackSelectionDialog;
    protected LinearLayout debugRootView;
    private DefaultTrackSelector trackSelector;

    private PlayerView playerView;
    private TracksInfo lastSeenTracksInfo;
    private DefaultTrackSelector.Parameters trackSelectionParameters;

    private static final String DRM_SCHEME_EXTRA = "drm_scheme";

    private static final String videoURI = "https://bitmovin-a.akamaihd.net/content/art-of-motion_drm/mpds/11331.mpd";
    private static final String licenseURI = "https://widevine-proxy.appspot.com/proxy";

    private boolean startAutoPlay;
    private int startItemIndex;
    private long startPosition;

    private static final String KEY_TRACK_SELECTION_PARAMETERS = "track_selection_parameters";
    private static final String KEY_ITEM_INDEX = "item_index";
    private static final String KEY_POSITION = "position";
    private static final String KEY_AUTO_PLAY = "auto_play";


    @Override
    public void onVisibilityChange(int visibility) {
        debugRootView.setVisibility(visibility);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        updateTrackSelectorParameters();
        updateStartPosition();
        outState.putBundle(KEY_TRACK_SELECTION_PARAMETERS, trackSelectionParameters.toBundle());
        outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay);
        outState.putInt(KEY_ITEM_INDEX, startItemIndex);
        outState.putLong(KEY_POSITION, startPosition);
    }


    private void updateStartPosition() {
        if (player != null) {
            startAutoPlay = player.getPlayWhenReady();
            startItemIndex = player.getCurrentMediaItemIndex();
            startPosition = Math.max(0, player.getContentPosition());
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        debugRootView = findViewById(R.id.controls_root);
        selectTracksButton = findViewById(R.id.select_tracks_button);
        selectTracksButton.setOnClickListener(this);

        if (savedInstanceState != null) {
            // Restore as DefaultTrackSelector.Parameters in case ExoPlayer specific parameters were set.
            trackSelectionParameters =
                    DefaultTrackSelector.Parameters.CREATOR.fromBundle(
                            savedInstanceState.getBundle(KEY_TRACK_SELECTION_PARAMETERS));
            startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY);
            startItemIndex = savedInstanceState.getInt(KEY_ITEM_INDEX);
            startPosition = savedInstanceState.getLong(KEY_POSITION);
        } else {
            trackSelectionParameters =
                    new DefaultTrackSelector.ParametersBuilder(/* context= */ this).build();
            clearStartPosition();
        }
    }

    protected void clearStartPosition() {
        startAutoPlay = true;
        startItemIndex = C.INDEX_UNSET;
        startPosition = C.TIME_UNSET;
    }

    @Override
    public void onStart() {
        super.onStart();
        initPlayer();
        if (Util.SDK_INT > 23) {
            if (playerView != null) {
                playerView.onResume();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (Util.SDK_INT <= 23 || player == null) {
            if (playerView != null) {
                playerView.onResume();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (Util.SDK_INT <= 23) {
            if (playerView != null) {
                playerView.onPause();
            }
            releasePlayer();
        }
    }


    @Override
    public void onStop() {
        super.onStop();
        if (Util.SDK_INT > 23) {
            if (playerView != null) {
                playerView.onPause();
            }
            releasePlayer();
        }
    }

    private void updateTrackSelectorParameters() {
        if (player != null) {
            // Until the demo app is fully migrated to TrackSelectionParameters, rely on ExoPlayer to use
            // DefaultTrackSelector by default.
            trackSelectionParameters =
                    (DefaultTrackSelector.Parameters) player.getTrackSelectionParameters();
        }
    }

    private void initPlayer() {
        trackSelector = new DefaultTrackSelector(/* context= */ this);
        lastSeenTracksInfo = TracksInfo.EMPTY;

        ExoPlayer player = new ExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .build();

        player.addListener(this);
        StyledPlayerView playerView = findViewById(R.id.player_view);
        playerView.setPlayer(player);
        DrmSessionManager drmSessionManager;



        player.setTrackSelectionParameters(trackSelectionParameters);

        MediaItem mediaItem = new MediaItem.Builder()
                .setUri(videoURI)
                .setDrmConfiguration(
                        new MediaItem.DrmConfiguration.Builder(C.WIDEVINE_UUID)
                                .setLicenseUri(licenseURI)
                                .build())
                .build();

/*
        HttpDataSource.Factory licenseDataSourceFactory = new DefaultHttpDataSource.Factory();

        CustomMediaDrmCallback drmCallback =
                new CustomMediaDrmCallback(licenseURI, licenseDataSourceFactory);
        drmSessionManager =
                new DefaultDrmSessionManager.Builder()
                        .setUuidAndExoMediaDrmProvider(C.WIDEVINE_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                        .build(drmCallback);


        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
        MediaSource mediaSource =
                new DashMediaSource.Factory(dataSourceFactory)
                        .setDrmSessionManager(drmSessionManager)
                        .createMediaSource(MediaItem.fromUri(videoURI));*/

        //player.setMediaSource(mediaSource);

        player.setMediaItem(mediaItem);
        player.prepare();
        player.play();
        playerView.setControllerVisibilityListener(this);
    }

    @Override
    public void onClick(View view) {
        if (view == selectTracksButton
                && !isShowingTrackSelectionDialog
                && TrackSelectionDialog.willHaveContent(trackSelector)) {

            isShowingTrackSelectionDialog = true;
            TrackSelectionDialog trackSelectionDialog =
                    TrackSelectionDialog.createForTrackSelector(
                            trackSelector,
                            /* onDismissListener= */ dismissedDialog -> isShowingTrackSelectionDialog = false);
            trackSelectionDialog.show(getSupportFragmentManager(), /* tag= */ null);
        }
    }

    private void updateButtonVisibility() {
        selectTracksButton.setEnabled(true);
    }
    private void showToast(int messageId) {
        showToast(getString(messageId));
    }

    private void showToast(String message) {
        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
    }

    @Override
    @SuppressWarnings("ReferenceEquality")
    public void onTracksInfoChanged(TracksInfo tracksInfo) {
        updateButtonVisibility();
        if (tracksInfo == lastSeenTracksInfo) {
            return;
        }
        if (!tracksInfo.isTypeSupportedOrEmpty(C.TRACK_TYPE_VIDEO)) {
            showToast("Unsupported Video");
        }
        if (!tracksInfo.isTypeSupportedOrEmpty(C.TRACK_TYPE_AUDIO)) {
            showToast("Unsupported Audio");
        }
        lastSeenTracksInfo = tracksInfo;
    }


    private void releasePlayer() {
        Assertions.checkNotNull(playerView).setPlayer(null);
        if (player != null) {
            player.release();
            player = null;
        }
    }
}