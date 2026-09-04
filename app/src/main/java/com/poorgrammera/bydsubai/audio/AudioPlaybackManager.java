package com.poorgrammera.bydsubai.audio;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AudioPlaybackManager {

    private static final String TAG = "AudioPlaybackManager";

    private static final int BYTES_PER_SAMPLE = 2;
    private static final int CHANNEL_COUNT = 1;
    private static final int BYTES_PER_FRAME =
            BYTES_PER_SAMPLE * CHANNEL_COUNT;

    /*
     * AudioTrack 내부 버퍼 목표 크기.
     *
     * 24kHz, mono, PCM16 기준:
     * 24,000 * 2 * 0.5 = 약 24KB
     */
    private static final int TRACK_BUFFER_DURATION_MS = 500;

    /*
     * 실제 재생 전에 확보할 PCM 길이.
     *
     * 120ms 정도면 시작 지연은 크지 않으면서
     * 짧은 네트워크 지터를 어느 정도 흡수할 수 있습니다.
     */
    private static final int PREBUFFER_DURATION_MS = 60;

    private static final int PCM_QUEUE_CAPACITY = 128;

    public interface PlaybackStateListener {
        void onPlaybackStarted();

        void onPlaybackFinished();
    }

    private final PlaybackStateListener listener;

    private final BlockingQueue<byte[]> pcmQueue =
            new ArrayBlockingQueue<>(PCM_QUEUE_CAPACITY);

    private final Object lifecycleLock = new Object();

    private final AtomicBoolean playbackStarted =
            new AtomicBoolean(false);

    private volatile boolean running;
    private volatile int lastSampleRate = 24000;

    private AudioTrack pcmTrack;
    private Thread writerThread;

    public AudioPlaybackManager(PlaybackStateListener listener) {
        this.listener = listener;
    }

    public void startPcmPlayback(int sampleRate) {
        stopPcmPlayback();
        this.lastSampleRate = sampleRate;

        int minBufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        if (minBufferSize <= 0) {
            throw new IllegalStateException(
                    "Invalid AudioTrack minBufferSize: " + minBufferSize
            );
        }

        int bytesPerSecond =
                sampleRate * CHANNEL_COUNT * BYTES_PER_SAMPLE;

        int desiredBufferSize =
                bytesPerSecond * TRACK_BUFFER_DURATION_MS / 1000;

        int trackBufferSize = Math.max(
                minBufferSize * 4,
                desiredBufferSize
        );

        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(
                        new AudioAttributes.Builder()
                                .setUsage(
                                        AudioAttributes
                                                .USAGE_ASSISTANCE_NAVIGATION_GUIDANCE
                                )
                                .setContentType(
                                        AudioAttributes.CONTENT_TYPE_SPEECH
                                )
                                .build()
                )
                .setAudioFormat(
                        new AudioFormat.Builder()
                                .setEncoding(
                                        AudioFormat.ENCODING_PCM_16BIT
                                )
                                .setSampleRate(sampleRate)
                                .setChannelMask(
                                        AudioFormat.CHANNEL_OUT_MONO
                                )
                                .build()
                )
                .setBufferSizeInBytes(trackBufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build();

        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            track.release();
            throw new IllegalStateException(
                    "AudioTrack initialization failed"
            );
        }

        int actualTrackBufferBytes =
                track.getBufferSizeInFrames() * BYTES_PER_FRAME;

        int desiredPrebufferBytes =
                bytesPerSecond * PREBUFFER_DURATION_MS / 1000;

        int prebufferBytes = Math.min(
                desiredPrebufferBytes,
                Math.max(BYTES_PER_FRAME, actualTrackBufferBytes / 2)
        );

        synchronized (lifecycleLock) {
            pcmQueue.clear();

            pcmTrack = track;
            running = true;
            playbackStarted.set(false);

            writerThread = new Thread(
                    () -> runWriter(track, prebufferBytes, sampleRate),
                    "Gemini-PcmWriter"
            );

            writerThread.start();
        }

        Log.i(
                TAG,
                "AudioTrack created"
                        + ", sampleRate=" + sampleRate
                        + ", minBuffer=" + minBufferSize
                        + ", requestedBuffer=" + trackBufferSize
                        + ", actualBuffer=" + actualTrackBufferBytes
                        + ", prebuffer=" + prebufferBytes
        );
    }

    /**
     * Gemini 수신 콜백에서는 큐에 넣고 즉시 반환합니다.
     */
    public void feedPcmData(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }

        if (!running || pcmTrack == null) {
            startPcmPlayback(lastSampleRate);
        }

        byte[] copiedData = Arrays.copyOf(data, data.length);

        if (!pcmQueue.offer(copiedData)) {
            Log.e(
                    TAG,
                    "PCM queue full. Dropping chunk"
                            + ", bytes=" + copiedData.length
                            + ", queueSize=" + pcmQueue.size()
            );
        }
    }

    private void runWriter(
            AudioTrack track,
            int prebufferBytes,
            int sampleRate
    ) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

        byte[] remainder = null;
        long totalFramesWritten = 0;

        try {
            ByteArrayOutputStream prebuffer =
                    new ByteArrayOutputStream(prebufferBytes);

            while (running && prebuffer.size() < prebufferBytes) {
                byte[] chunk = pcmQueue.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (chunk == null) {
                    // A live session may be idle for an arbitrary time before its
                    // first response. Do not start an empty AudioTrack: doing so
                    // emits playback callbacks and incorrectly suppresses the mic.
                    continue;
                }

                int required = prebufferBytes - prebuffer.size();
                int bytesToPrebuffer = Math.min(required, chunk.length);

                prebuffer.write(chunk, 0, bytesToPrebuffer);

                if (bytesToPrebuffer < chunk.length) {
                    remainder = Arrays.copyOfRange(
                            chunk,
                            bytesToPrebuffer,
                            chunk.length
                    );
                }
            }

            if (!running) {
                return;
            }

            byte[] initialData = prebuffer.toByteArray();

            if (initialData.length == 0) {
                return;
            }

            if (!writeFully(track, initialData)) {
                return;
            }
            totalFramesWritten += initialData.length / BYTES_PER_FRAME;

            track.play();
            notifyPlaybackStarted();

            if (remainder != null && remainder.length > 0) {
                if (!writeFully(track, remainder)) {
                    return;
                }
                totalFramesWritten += remainder.length / BYTES_PER_FRAME;
            }

            while (running) {
                byte[] chunk = pcmQueue.poll(200, java.util.concurrent.TimeUnit.MILLISECONDS);

                if (chunk != null) {
                    if (writeFully(track, chunk)) {
                        totalFramesWritten += chunk.length / BYTES_PER_FRAME;
                    } else {
                        break;
                    }
                } else {
                    // PCM Queue has been empty for 200ms
                    // Check if AudioTrack has played all written frames
                    long headPos = track.getPlaybackHeadPosition() & 0xFFFFFFFFL;
                    if (headPos >= totalFramesWritten - (sampleRate * 0.05)) {
                        Log.i(TAG, "AudioTrack finished playing all queued frames (headPos=" + headPos + ", totalWritten=" + totalFramesWritten + "). Stopping writer.");
                        break;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            Log.e(TAG, "PCM writer failed", e);
        } finally {
            try {
                if (track.getState() == AudioTrack.STATE_INITIALIZED) {
                    track.stop();
                }
            } catch (Exception ignored) {}
            synchronized (lifecycleLock) {
                running = false;
                if (pcmTrack == track) {
                    pcmTrack = null;
                }
                writerThread = null;
                pcmQueue.clear();
            }
            notifyPlaybackFinished();
        }
    }

    private boolean writeFully(
            AudioTrack track,
            byte[] data
    ) {
        int offset = 0;

        while (running && offset < data.length) {
            int written = track.write(
                    data,
                    offset,
                    data.length - offset,
                    AudioTrack.WRITE_BLOCKING
            );

            if (written > 0) {
                offset += written;
                continue;
            }

            if (written == 0) {
                Thread.yield();
                continue;
            }

            Log.e(
                    TAG,
                    "AudioTrack.write failed"
                            + ", result=" + written
                            + ", offset=" + offset
                            + ", requested=" + data.length
            );

            return false;
        }

        return offset == data.length;
    }

    public void stopPcmPlayback() {
        final AudioTrack track;
        final Thread thread;

        synchronized (lifecycleLock) {
            running = false;

            track = pcmTrack;
            thread = writerThread;

            pcmTrack = null;
            writerThread = null;

            pcmQueue.clear();
        }

        /*
         * blocking write를 먼저 해제합니다.
         */
        if (track != null) {
            try {
                if (track.getState() == AudioTrack.STATE_INITIALIZED) {
                    track.pause();
                    track.flush();
                    track.stop();
                }
            } catch (IllegalStateException e) {
                Log.w(TAG, "Failed to stop AudioTrack cleanly", e);
            }
        }

        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();

            try {
                thread.join(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (track != null) {
            try {
                track.release();
            } catch (Exception e) {
                Log.w(TAG, "Failed to release AudioTrack", e);
            }
        }

        notifyPlaybackFinished();
    }

    private void notifyPlaybackStarted() {
        if (playbackStarted.compareAndSet(false, true)) {
            if (listener != null) {
                listener.onPlaybackStarted();
            }
        }
    }

    private void notifyPlaybackFinished() {
        if (playbackStarted.compareAndSet(true, false)) {
            if (listener != null) {
                listener.onPlaybackFinished();
            }
        }
    }

    public boolean isPlaying() {
        return playbackStarted.get();
    }

    public int getQueuedChunkCount() {
        return pcmQueue.size();
    }

    public int getUnderrunCount() {
        AudioTrack track = pcmTrack;

        if (track == null
                || android.os.Build.VERSION.SDK_INT
                < android.os.Build.VERSION_CODES.N) {
            return 0;
        }

        try {
            return track.getUnderrunCount();
        } catch (Exception e) {
            return 0;
        }
    }
}
