package org.hackyourlife.gcn.dsp.input;

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * Created by Nick on 14 nov. 2019.
 * Copyright © ImSpooks
 */
public abstract class InputData {

    private InputData data;

    public static InputData getInputData(InputStream stream) {
        return new InputDataStream(stream);
    }

    public static InputData getInputData(RandomAccessFile file) {
        return new InputDataFile(file);
    }

    public abstract long length();
    public abstract int read() throws IOException;
    public abstract int read(byte[] data) throws IOException;
    public abstract void seek(long pos) throws IOException;
    public abstract void close() throws IOException;

    private static class InputDataStream extends InputData {
        private InputStream stream;

        private long filepos;
        private long length;

        public InputDataStream(InputStream stream) {
            this.stream = stream;

            try {
                this.length = this.stream.available();
                this.stream.mark(Math.toIntExact(this.length));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public long length() {
            return this.length;
        }

        @Override
        public int read() throws IOException {
            return this.stream.read();
        }

        @Override
        public int read(byte[] data) throws IOException {
            return this.stream.read(data);
        }

        @Override
        public void seek(long pos) throws IOException {
            this.stream.reset();
            if (pos > 0) {
                this.stream.skip(pos);
            }
        }

        @Override
        public void close() throws IOException {
            this.stream.close();
        }

        public long getFilepos() {
            return this.filepos;
        }

        public void setFilepos(long filepos) {
            this.filepos = filepos;
        }
    }



    private static class InputDataFile extends InputData {
        private RandomAccessFile randomAccessFile;

        private long filepos;
        private long length;

        public InputDataFile(RandomAccessFile randomAccessFile) {
            this.randomAccessFile = randomAccessFile;

            try {
                this.length = this.randomAccessFile.length();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        @Override
        public long length() {
            return this.length;
        }

        @Override
        public int read() throws IOException {
            return this.randomAccessFile.read();
        }

        @Override
        public int read(byte[] data) throws IOException {
            return this.randomAccessFile.read(data);
        }

        @Override
        public void seek(long pos) throws IOException {
            this.randomAccessFile.seek(pos);
        }

        @Override
        public void close() throws IOException {
            this.randomAccessFile.close();
        }
    }
}