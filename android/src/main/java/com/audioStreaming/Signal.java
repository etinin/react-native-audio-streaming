package com.audioStreaming;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.RemoteControlClient;
import android.net.Uri;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnInfoListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.audioStreaming.Helpers.AudioManagerHelper;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.upstream.DefaultAllocator;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;

//import com.spoledge.aacdecoder.MultiPlayer;
//import com.spoledge.aacdecoder.PlayerCallback;

public class Signal extends Service
        {


    // Notification
    private Class<?> clsActivity;
    private static final int NOTIFY_ME_ID = 1081;
    private NotificationCompat.Builder notifyBuilder;
    private NotificationManager notifyManager = null;
            private PlaybackStateCompat mPlaybackState;
    public static RemoteViews remoteViews;


    public static final String BROADCAST_PLAYBACK_STOP = "stop",
            BROADCAST_PLAYBACK_PLAY = "pause",
            BROADCAST_EXIT = "exit";

    private final Handler handler = new Handler();
    private final IBinder binder = new RadioBinder();
    private final SignalReceiver receiver = new SignalReceiver(this);
    private Context context;
    private String streamingURL;
    public boolean isPlaying = false;
    private boolean didPrepare = false;
    private boolean didSetDataSource = false;
    private boolean isPreparingStarted = false;
    private EventsReceiver eventsReceiver;
    private ReactNativeAudioStreamingModule module;

            private MediaSessionCompat mMediaSession;
            private AudioManager mAudioManager;
            private WifiManager.WifiLock mWifiLock;
            private PowerManager.WakeLock mWakeLock;

    private TelephonyManager phoneManager;
    //private PhoneListener phoneStateListener;
    private SimpleExoPlayer player;

            //AudioManager.
            private AudioManagerHelper mAudioManagerHelper;





    public void setData(Context context, ReactNativeAudioStreamingModule module) {
        this.context = context;
        this.clsActivity = module.getClassActivity();
        this.module = module;

        this.eventsReceiver = new EventsReceiver(this.module);


        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.CREATED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.DESTROYED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.STARTED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.CONNECTING));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.START_PREPARING));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.PREPARED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.PLAYING));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.STOPPED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.COMPLETED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.ERROR));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.BUFFERING_START));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.BUFFERING_END));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.METADATA_UPDATED));
        registerReceiver(this.eventsReceiver, new IntentFilter(Mode.ALBUM_UPDATED));


//        this.phoneStateListener = new PhoneListener(this.module);
//        this.phoneManager = (TelephonyManager) getSystemService(TELEPHONY_SERVICE);
//        if (this.phoneManager != null) {
//            this.phoneManager.listen(this.phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
//        }


    }
            private static final int DEFAULT_BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 5000;
            private static final int DEFAULT_BUFFER_FOR_PLAYBACK_MS = 5000;
            private static final int DEFAULT_MAX_BUFFER_MS = 10000;
            private static final int DEFAULT_MIN_BUFFER_MS = 5000;


    @Override
    public void onCreate() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BROADCAST_PLAYBACK_STOP);
        intentFilter.addAction(BROADCAST_PLAYBACK_PLAY);
        intentFilter.addAction(BROADCAST_EXIT);
        intentFilter.addAction(BROADCAST_EXIT);
        registerReceiver(this.receiver, intentFilter);
        // setup our media session



        try {
            mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);

            WifiManager wm = (WifiManager) (getApplicationContext()).getSystemService(Context.WIFI_SERVICE);
            mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "radioMelodiaWifilock");

            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);

            mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "radioMelodiaWakelock");

            // set default playback state
            mPlaybackState = new PlaybackStateCompat.Builder()
                    .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                    .build();

            // setup our media session
            mMediaSession = new MediaSessionCompat(this, Signal.class.getSimpleName());
            Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON);
            PendingIntent mbrIntent = PendingIntent.getBroadcast(this, 0, mediaButtonIntent, 0);
            mMediaSession.setMediaButtonReceiver(mbrIntent);
            mMediaSession.setCallback( new MediaSessionCompat.Callback() {
                @Override
                public void onPlay() {
                    play();
                }

                @Override
                public void onPause() {
                    stop();
                }

                @Override
                public void onSkipToNext() {

                }

                @Override
                public void onSkipToPrevious() {

                }

                @Override
                public void onStop() {
                    stop();
                }

                @Override
                public void onSeekTo(long pos) {

                }

                @Override
                public boolean onMediaButtonEvent(Intent mediaButtonEvent) {
                    sendBroadcast(mediaButtonEvent);
                    return true;
                }
            });

            mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                    MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
            mMediaSession.setActive(true);
            mMediaSession.setPlaybackState(mPlaybackState);
        } catch (Exception e) {
            e.printStackTrace();
        }


        this.notifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
//        try {
//            java.net.URL.setURLStreamHandlerFactory(new java.net.URLStreamHandlerFactory() {
//                public java.net.URLStreamHandler createURLStreamHandler(String protocol) {
//                    if ("icy".equals(protocol)) {
//                        return new com.spoledge.aacdecoder.IcyURLStreamHandler();
//                    }
//                    return null;
//                }
//            });
//        } catch (Throwable t) {
//
//        }

        sendBroadcast(new Intent(Mode.CREATED));
    }

    public void setURLStreaming(String streamingURL) {
        this.streamingURL = streamingURL;
    }

    boolean mVirgin = true;

    public void play() {
        Log.v("MELODIA STOP", "melodia playing");
//        if(mVirgin) {
            startForeground(NOTIFY_ME_ID, getNotification());
            mVirgin = false;
//        }
        if (isConnected() && !this.isPlaying) {
            this.prepare();
        } else {
            sendBroadcast(new Intent(Mode.STOPPED));
            Toast.makeText(this, "Impossível reproduzir a rádio. Verifique sua conexão com a internet.", Toast.LENGTH_LONG).show();
        }

        this.isPlaying = true;
    }

    public void stop() {
        this.isPreparingStarted = false;
        Log.v("MELODIA STOP", "melodia stopping");
        if (this.isPlaying) {
            this.isPlaying = false;
            this.player.setPlayWhenReady(false);
            this.mPlaybackState = new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_PAUSED, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f).setActions(PlaybackStateCompat.ACTION_PLAY).build();

            this.mMediaSession.setPlaybackState(this.mPlaybackState);
            if(mWifiLock!=null && mWifiLock.isHeld()) {

                try{
                    mWifiLock.release();
                }
                catch (Exception e) {

                }

            }

            if(mWakeLock!=null && mWakeLock.isHeld()) {
                try {
                    mWakeLock.release();
                }
                catch(Exception e) {

                }
            }
            stopForeground(false);
        }


        sendBroadcast(new Intent(Mode.STOPPED));
    }

    public NotificationManager getNotifyManager() {
        return notifyManager;
    }

    public class RadioBinder extends Binder {
        public Signal getService() {
            return Signal.this;
        }
    }

    public void showNotificatin() {

    }

    public Notification getNotification() {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel("com.audioStreaming", "Audio Streaming",
                            NotificationManager.IMPORTANCE_HIGH);
            if (notifyManager != null) {
                notifyManager.createNotificationChannel(channel);
            }

//            notifyBuilder.setChannelId("com.audioStreaming");
//            notifyBuilder.setOnlyAlertOnce(true);

        }
        remoteViews = new RemoteViews(context.getPackageName(), R.layout.streaming_notification_player);
        notifyBuilder = new NotificationCompat.Builder(this, "com.audioStreaming")
                .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off) // TODO Use app icon instead
                .setContentText("")
                .setOngoing(true)
                .setContent(remoteViews).setOnlyAlertOnce(true);

        Intent resultIntent = new Intent(this, this.clsActivity);
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        resultIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
        stackBuilder.addParentStack(this.clsActivity);
        stackBuilder.addNextIntent(resultIntent);

        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
                PendingIntent.FLAG_UPDATE_CURRENT);

        notifyBuilder.setContentIntent(resultPendingIntent);
        remoteViews.setOnClickPendingIntent(R.id.btn_streaming_notification_play, makePendingIntent(BROADCAST_PLAYBACK_PLAY));
        remoteViews.setOnClickPendingIntent(R.id.btn_streaming_notification_stop, makePendingIntent(BROADCAST_EXIT));
        notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notifyManager.cancelAll();

        Notification notification = notifyBuilder.build();
        notification.flags = Notification.FLAG_FOREGROUND_SERVICE |
                Notification.FLAG_NO_CLEAR |
                Notification.FLAG_ONGOING_EVENT;

        return notification;
    }

    private PendingIntent makePendingIntent(String broadcast) {
        Intent intent = new Intent(broadcast);
        return PendingIntent.getBroadcast(this.context, 0, intent, 0);
    }

    public void clearNotification() {
//        if (notifyManager != null)
//            notifyManager.cancel(NOTIFY_ME_ID);
    }

    public void exitNotification() {
        stopForeground(true);
        notifyManager.cancelAll();
//        clearNotification();
        notifyBuilder = null;
        notifyManager = null;
    }

            private AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {

                @Override
                public void onAudioFocusChange(int focusChange) {
                    if (focusChange==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        //We've temporarily lost focus, so pause the mMediaPlayer, wherever it's at.
                        try {
                            player.setPlayWhenReady(false);
                            mAudioManagerHelper.setHasAudioFocus(false);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                    } else if (focusChange==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                        //Lower the current mMediaPlayer volume.
                        mAudioManagerHelper.setAudioDucked(true);
                        mAudioManagerHelper.setTargetVolume(5);
                        mAudioManagerHelper.setStepDownIncrement(1);


                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int)0.8f*mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC), 0);

                    } else if (focusChange==AudioManager.AUDIOFOCUS_GAIN) {

                        if (mAudioManagerHelper.isAudioDucked()) {
                            //Crank the volume back up again.

                            mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (int)1.25f*mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC), 0);
                            //mHandler.post(duckUpVolumeRunnable);
                            mAudioManagerHelper.setAudioDucked(false);
                        } else {
                            //We've regained focus. Update the audioFocus tag, but don't start the mMediaPlayer.
                            if(isPlaying) {
                                player.setPlayWhenReady(true);
                            }

                        }

                    } else if (focusChange==AudioManager.AUDIOFOCUS_LOSS) {
                        //We've lost focus permanently so pause the service. We'll have to request focus again later.
                        player.setPlayWhenReady(false);

                        mAudioManagerHelper.setHasAudioFocus(false);

                    }

                }

            };

    public boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        if (netInfo != null && netInfo.isConnectedOrConnecting()) {
            return true;
        }
        return false;
    }
            /**
             * Requests AudioFocus from the OS.
             *
             * @return True if AudioFocus was gained. False, otherwise.
             */
            private boolean requestAudioFocus() {
                int result = mAudioManager.requestAudioFocus(audioFocusChangeListener,
                        AudioManager.STREAM_MUSIC,
                        AudioManager.AUDIOFOCUS_GAIN);

                if (result!=AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                    Toast.makeText(this, "Sem permissão para tocar a rádio! Feche outros aplicativos de áudio.", Toast.LENGTH_LONG).show();
                    //Stop the service.
                    this.stopSelf();

                    return false;
                } else {
                    return true;
                }

            }

    public void prepare() {
        if(this.isPlaying || isPreparingStarted) {
            return;
        }
        /* ------Station- buffering-------- */
        this.isPreparingStarted = true;
        sendBroadcast(new Intent(Mode.START_PREPARING));

        try {
//            this.mediaPlayer.reset();
            if(!this.didSetDataSource) {
                //this.mediaPlayer.setDataSource(this.streamingURL);
                this.didSetDataSource = true;
            }

            try {
                if (this.mWifiLock != null && (!this.mWifiLock.isHeld())) {
                    this.mWifiLock.acquire();
                }
            }
            catch(Exception e) {

                System.out.println("serious trouble");

            }
            try {
                if (this.mWakeLock != null && (!this.mWakeLock.isHeld())) {
                    this.mWakeLock.acquire();
                }
            }
            catch(Exception e) {
                System.out.println("serious trouble");
            }

            if(!this.didPrepare) {

                DefaultExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
                DefaultTrackSelector trackSelector = new DefaultTrackSelector();

                ExtractorMediaSource mediaSource = new ExtractorMediaSource.Factory(new DefaultHttpDataSourceFactory("Chrome/60.0.3112.113")).createMediaSource(Uri.parse(this.streamingURL));
                AudioAttributes.Builder contentType = new AudioAttributes.Builder().setContentType(2);
                contentType.setUsage(android.media.AudioAttributes.USAGE_MEDIA);
                DefaultLoadControl.Builder builder = new DefaultLoadControl.Builder();
                builder.setAllocator(new DefaultAllocator(true, 64*1024));
                builder.setBufferDurationsMs(5000, 25000, 5000, 5000);
                builder.setPrioritizeTimeOverSizeThresholds(true);
                this.player = ExoPlayerFactory.newSimpleInstance( this, new DefaultRenderersFactory(this),trackSelector, builder.createDefaultLoadControl());
                this.player.setAudioAttributes(contentType.build());


                this.player.prepare(mediaSource);


            }
            if(requestAudioFocus()) {
                this.player.setPlayWhenReady(true);
                this.mPlaybackState = new PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f).setActions(PlaybackStateCompat.ACTION_PAUSE).build();

                this.mMediaSession.setPlaybackState(this.mPlaybackState);
                this.isPlaying = true;
            }

            this.isPreparingStarted = false;



        } catch (Exception e) {
            e.printStackTrace();
            stop();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (this.isPlaying) {
            sendBroadcast(new Intent(Mode.PLAYING));
        } else if (this.isPreparingStarted) {
            sendBroadcast(new Intent(Mode.START_PREPARING));
        } else {
            sendBroadcast(new Intent(Mode.STARTED));
        }

        return Service.START_STICKY;
    }

   // @Override
    public void onPrepared(MediaPlayer _mediaPlayer) {
        this.isPreparingStarted = false;
        this.didPrepare = true;
        //this.mediaPlayer.start();
        sendBroadcast(new Intent(Mode.PREPARED));
    }

   // @Override
    public void onCompletion(MediaPlayer mediaPlayer) {
        if(this.isPlaying) {
            this.isPlaying = false;
            //this.mediaPlayer.pause();
        }

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancelAll();

        sendBroadcast(new Intent(Mode.COMPLETED));
    }

   // @Override
    public boolean onInfo(MediaPlayer mp, int what, int extra) {
        if (what == 701) {
            this.isPlaying = false;
            sendBroadcast(new Intent(Mode.BUFFERING_START));
        } else if (what == 702) {
            this.isPlaying = true;
            sendBroadcast(new Intent(Mode.BUFFERING_END));
        }
        return false;
    }

   // @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        switch (what) {
            case MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK:
                //Log.v("ERROR", "MEDIA ERROR NOT VALID FOR PROGRESSIVE PLAYBACK "	+ extra);
                break;
            case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                //Log.v("ERROR", "MEDIA ERROR SERVER DIED " + extra);
                break;
            case MediaPlayer.MEDIA_ERROR_UNKNOWN:
                //Log.v("ERROR", "MEDIA ERROR UNKNOWN " + extra);
                break;
        }
        sendBroadcast(new Intent(Mode.ERROR));
        return false;
    }

    //
    public void playerStarted() {
        //  TODO
    }

    //
    public void playerPCMFeedBuffer(boolean isPlaying, int bufSizeMs, int bufCapacityMs) {
        if (isPlaying) {
            this.isPreparingStarted = false;
            if (bufSizeMs < 500) {
                this.isPlaying = false;
                sendBroadcast(new Intent(Mode.BUFFERING_START));
                //buffering
            } else {
                this.isPlaying = true;
                sendBroadcast(new Intent(Mode.PLAYING));
                //playing
            }
        } else {
            //buffering
            this.isPlaying = false;
            sendBroadcast(new Intent(Mode.BUFFERING_START));
        }
    }

    //old garbage start
    public void playerException(final Throwable t) {
        this.isPlaying = false;
        this.isPreparingStarted = false;
        sendBroadcast(new Intent(Mode.ERROR));
        //  TODO
    }


    public void playerMetadata(final String key, final String value) {
        Intent metaIntent = new Intent(Mode.METADATA_UPDATED);
        metaIntent.putExtra("key", key);
        metaIntent.putExtra("value", value);
        sendBroadcast(metaIntent);

        if (key != null && key.equals("StreamTitle") && remoteViews != null && value != null) {
            remoteViews.setTextViewText(R.id.song_name_notification, value);
            notifyBuilder.setContent(remoteViews);
            notifyManager.notify(NOTIFY_ME_ID, notifyBuilder.build());
        }
    }


    public void playerAudioTrackCreated(AudioTrack atrack) {
        //  TODO
    }

            @Override
            public void onDestroy() {
                super.onDestroy();
                if (this.receiver != null)
                    unregisterReceiver(this.receiver);
            }

    public void playerStopped(int perf) {
        this.isPlaying = false;
        this.isPreparingStarted = false;
        sendBroadcast(new Intent(Mode.STOPPED));
        //  TODO
    }

            @Override
            public void onTaskRemoved(Intent rootIntent) {
                super.onTaskRemoved(rootIntent);
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                notificationManager.cancelAll();
            }


}
