package org.hackyourlife.gcn.dsp.player;

import org.hackyourlife.gcn.dsp.Stream;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by Nick on 14 nov. 2019.
 * Copyright © ImSpooks
 */
public class BrstmPlayer {

    private Stream stream;

    private Clip clip;
    private boolean paused = false;
    private long position;
    private int track = -1;

    /**
     *
     * @param stream The stream that it needs to run, e.g. BRSTM or BFSTM
     * @param track The track/channel that plays, plays all tracks if it is set to -1
     */
    public BrstmPlayer(Stream stream, int track) {
        this.stream = stream;
        this.track = track;

        this.initialize();
    }

    public BrstmPlayer(Stream stream) {
        this(stream, -1);
    }

    /**
     * Starts the audio player
     */
    public void start() {
        this.clip.start();
    }

    /**
     * Stops the audio player
     */
    public void stop() {
        this.clip.stop();
    }

    /**
     * Paused the audio player
     */
    public void pause() {
        if (this.paused)
            throw new UnsupportedOperationException("Audio already paused");

        this.position = this.clip.getFramePosition();
        this.stop();
        this.paused = true;
    }

    /**
     * Resumes the audio player
     */
    public void resume() {
        if (!this.paused)
            throw new UnsupportedOperationException("Audio already playing");

        this.clip.setFramePosition(Math.toIntExact(this.position));
        this.start();
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

        FloatControl gain = (FloatControl) this.clip.getControl(FloatControl.Type.MASTER_GAIN);
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
     * Initializes the audio file to be played. Time to initialize depends on your computer performance and file size.
     */
    private void initialize() {
        try {
            long loop_start_sample;
            long loop_end_sample;

            try {
                // getting start sample
                Field startSample = stream.getClass().getDeclaredField("loop_start_sample");
                startSample.setAccessible(true);
                loop_start_sample = startSample.getLong(stream);

                // getting end sample
                Field endSample = stream.getClass().getDeclaredField("loop_end_sample");
                endSample.setAccessible(true);
                loop_end_sample = endSample.getLong(stream);
            } catch (Exception e) {
                try {
                    // getting start sample
                    Field startSample = stream.getClass().getDeclaredField("loop_start_offset");
                    startSample.setAccessible(true);
                    loop_start_sample = Math.round(startSample.getLong(stream) / 8.0 * 14.0);

                    // getting end sample
                    Field endSample = stream.getClass().getDeclaredField("loop_end_offset");
                    endSample.setAccessible(true);
                    loop_end_sample = Math.round(endSample.getLong(stream) / 8.0 * 14.0);
                } catch (Exception ex) {
                    throw new UnsupportedOperationException("Unsupported stream found.");
                }
            }

            int channels = stream.getChannels();
            if(channels > 2) {
                channels = 2;
            }
            AudioFormat format = new AudioFormat(
                    AudioFormat.Encoding.PCM_SIGNED,	// encoding
                    stream.getSampleRate(),			// sample rate
                    16,					// bit/sample
                    channels,				// channels
                    2 * channels,
                    stream.getSampleRate(),
                    true					// big-endian
            );

            // set loop to false
            Field field = stream.getClass().getDeclaredField("loop_flag");
            field.setAccessible(true);
            field.setInt(stream, 0);

            List<Byte> data = new ArrayList<>();
            while (stream.hasMoreData()) {
                byte[] buffer = stream.decode();
                for (byte b : sum(buffer, stream.getChannels(), track)) {
                    // putting all bytes in to an arraylist
                    data.add(b);
                }
            }

            // converting arraylist back to array
            byte[] result = new byte[data.size()];
            for (int i = 0; i < data.size(); i++) {
                result[i] = data.get(i);
            }

            ByteArrayInputStream byteStream = new ByteArrayInputStream(result);

            AudioInputStream sound = new AudioInputStream(byteStream, format,
                    result.length);

            // creating audio clip with brstm's audio data
            DataLine.Info info = new DataLine.Info(Clip.class, sound.getFormat());
            clip = (Clip) AudioSystem.getLine(info);
            clip.open(sound);
            // setting looping point
            clip.setLoopPoints(Math.toIntExact(loop_start_sample), Math.toIntExact(loop_end_sample));
            clip.loop(Clip.LOOP_CONTINUOUSLY);
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