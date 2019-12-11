package org.hackyourlife.gcn.dsp.player;

import org.hackyourlife.gcn.dsp.AsyncDecoder;
import org.hackyourlife.gcn.dsp.Stream;

import javax.sound.sampled.*;
import java.lang.reflect.Field;

/**
 * Created by Nick on 11 dec. 2019.
 * Copyright © ImSpooks
 */
public class BrstmPlayer {

    private Stream stream;
    private Thread asyncThread;
    private boolean paused = false;
    private int track;
    private AsyncDecoder decoder;
    private SourceDataLine waveout;
    private boolean shouldStop = false;

    /**
     * Constructor for the brstm player
     * @param stream The stream that it needs to run, e.g. BRSTM or BFSTM
     * @param track The track/channel that plays, plays all tracks if it is set to -1
     */
    public BrstmPlayer(Stream stream, int track) {
        this.stream = stream;
        this.track = track;
    }

    public BrstmPlayer(Stream stream) {
        this(stream, -1);
    }

    /**
     * Starts the audio player
     */
    public void start() {
        // check if result isnt null
        if (this.stream == null) {
            throw new NullPointerException("Cannot handle BRSTM player for an empty or undefined stream");
        }

        // if audio is already playing it resets

        // setting up a async thread so the current doesn't freeze so other code in the same thread can continue
        this.asyncThread = new Thread(() -> {
            // starting the brstm file
            paused = false;
            shouldStop = false;
            this.decoder = new AsyncDecoder(stream);
            this.decoder.start();
            this.play(decoder);

        });
        this.asyncThread.start();
    }

    /**
     * Stops the audio player
     */
    public void stop() {
        this.shouldStop = true;
        this.asyncThread.interrupt();
        waveout.stop();

        try {
            // resets position back to 0
            Field field = stream.getClass().getDeclaredField("current_byte");
            field.setAccessible(true);
            field.setLong(stream, 0);
        } catch (NoSuchFieldException | IllegalAccessException ignored) {}
    }

    /**
     * Paused the audio player
     */
    public void pause() {
        this.paused = true;
    }

    /**
     * Resumes the audio player
     */
    public void resume() {
        this.paused = false;
    }

    /**
     * Change the volume of the audio player
     * If percentage isnt between 0.0F and 1.0F it forces it to. using {@code Math.max(0.0F, Math.min(1.0F, percentage))}
     *
     * @param percentage between 0.0F and 1.0F
     */
    public void setVolume(float percentage) {
        percentage = Math.max(0.0F, Math.min(1.0F, percentage));

        FloatControl gain = (FloatControl) this.waveout.getControl(FloatControl.Type.MASTER_GAIN);
        float dB = (float) (Math.log(percentage) / Math.log(gain.getMaximum()) * 20);
        gain.setValue(dB);
    }

    /**
     * Check if the audio player is paused
     * @return audio player is paused
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * @return The track/channel that is playing
     */
    public int getTrack() {
        return track;
    }

    /**
     * @return The original brstm/bfstm stream
     */
    public Stream getStream() {
        return stream;
    }

    /**
     * @return The thread the music is playing on
     */
    public Thread getAsyncThread() {
        return asyncThread;
    }

    /**
     * Closes the audio stream
     */
    public void close() {
        shouldStop = true;
        try {
            stream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        waveout.stop();
    }

    /**
     * @param stream Decoder stream {@link AsyncDecoder}
     */
    private void play(AsyncDecoder stream) {
        try {
            int channels = stream.getChannels();
            if(channels > 2) {
                channels = 2;
            }
            AudioFormat format = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,	// encoding
                    stream.getSampleRate(),			    // sample rate
                    16,					// bit/sample
                    channels,				            // channels
                    2 * channels,
                    stream.getSampleRate(),
                    true					    // big-endian
            );

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            if(!AudioSystem.isLineSupported(info)) {
                throw new Exception("Line matching " + info + " not supported");
            }

            this.waveout = (SourceDataLine) AudioSystem.getLine(info);
            waveout.open(format, 16384);

            waveout.start();
            main: while(!shouldStop && stream.hasMoreData()) {
                if (stream.isInterrupted()) {
                    break;
                }

                System.out.println("asyncThread.isInterrupted() = " + asyncThread.isInterrupted());

                if (paused)
                    continue;

                byte[] buffer = stream.decode();
                if (buffer == null)
                    continue;

                buffer = sum(buffer, stream.getChannels(), track);

                // write each byte individually to make sure pausing works at an instant.
                for (int i = 0; i < buffer.length; i+=4) {
                    if (shouldStop)
                        break main;

                    if (paused)
                        break;
                    waveout.write(new byte[] {
                            buffer[i],
                            buffer[i + 1],
                            buffer[i + 2],
                            buffer[i + 3]
                    }, 0, 4);
                }
            }
            this.stop();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private byte[] sum(byte[] data, int channels, int track) {
        if(channels == 1 || channels == 2) {
            return data;
        }
        int samples = data.length / (channels * 2);
        byte[] result = new byte[samples * 4]; // 2 channels, 16bit

        // extract single (stereo) track?
        if(track != -1) {
            int ch = track * 2;
            for(int i = 0; i < samples; i++) {
                int lidx = (i * channels + ch) * 2;
                int ridx = (i * channels + ch + 1) * 2;
                result[i * 4    ] = data[lidx];
                result[i * 4 + 1] = data[lidx + 1];
                result[i * 4 + 2] = data[ridx];
                result[i * 4 + 3] = data[ridx + 1];
            }
            return result;
        }

        // sum up all channels
        for(int i = 0; i < samples; i++) {
            int l = 0;
            int r = 0;
            for(int ch = 0; ch < channels; ch++) {
                int idx = (i * channels + ch) * 2;
                short val = (short) (Byte.toUnsignedInt(data[idx]) << 8 | Byte.toUnsignedInt(data[idx + 1]));
                if((ch & 1) == 0) {
                    l += val;
                } else {
                    r += val;
                }
            }
            // clamp
            if(l < -32768) {
                l = -32768;
            } else if(l > 32767) {
                l = 32767;
            }
            if(r < -32768) {
                r = -32768;
            } else if(r > 32767) {
                r = 32767;
            }
            // write back
            result[i * 4] = (byte) (l >> 8);
            result[i * 4 + 1] = (byte) l;
            result[i * 4 + 2] = (byte) (r >> 8);
            result[i * 4 + 3] = (byte) r;
        }
        return result;
    }
}